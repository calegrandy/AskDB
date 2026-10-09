package io.github.calegrandy.askdb.query;

import io.github.calegrandy.askdb.model.QueryAttempt;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapperResultSetExtractor;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The tool Claude calls to run SQL against one database. One instance per question:
 * it remembers the last successful query so the API can return it with the answer.
 *
 * <p>Generated SQL is untrusted, so it runs behind several layers, each covering what the one before can miss:
 * <ol>
 *   <li>a text check that it starts with SELECT or WITH, mainly to give the model a clear error to correct;</li>
 *   <li>a prepared statement, which PostgreSQL limits to a single statement;</li>
 *   <li>a read-only transaction that is always rolled back, so the database rejects any write;</li>
 *   <li>a statement timeout, and a cap on returned rows.</li>
 * </ol>
 * None of this limits what can be <em>read</em>: connect with a database user that can only read what it should.
 */
public class SqlTool {

    public static final int MAX_ROWS = 100;
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private static final Pattern READ_QUERY = Pattern.compile("^(SELECT|WITH)\\b", Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate jdbcTemplate;
    private final Duration timeout;
    private final List<QueryAttempt> attempts = new ArrayList<>();
    private String lastSql;
    private List<Map<String, Object>> lastRows = List.of();

    public SqlTool(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, DEFAULT_TIMEOUT);
    }

    public SqlTool(JdbcTemplate jdbcTemplate, Duration timeout) {
        this.jdbcTemplate = jdbcTemplate;
        this.timeout = timeout;
    }

    @Tool(name = "run_sql", description = """
            Runs one read-only PostgreSQL SELECT statement (it may start with WITH) and returns the result rows as JSON. \
            At most 100 rows are returned, and queries running longer than 10 seconds are cancelled. \
            On failure, returns the database error message.""")
    public Object runSql(@ToolParam(description = "A single PostgreSQL SELECT statement") String sql) {
        long start = System.nanoTime();
        String query = withoutTrailingSemicolon(sql.strip());
        if (!READ_QUERY.matcher(query).find()) {
            return failed(query, "Only SELECT queries (optionally starting with WITH) are allowed.", start);
        }

        try {
            List<Map<String, Object>> rows = jdbcTemplate.execute((ConnectionCallback<List<Map<String, Object>>>)
                            connection -> runReadOnly(connection, query))
                    .stream()
                    .map(SqlTool::toPlainValues)
                    .toList();
            lastSql = query;
            lastRows = rows;
            attempts.add(QueryAttempt.succeeded(query, rows.size(), elapsedMillis(start)));
            return rows;
        } catch (DataAccessException e) {
            // Returned, not thrown, so the model can read the error and fix its query.
            return failed(query, e.getMostSpecificCause().getMessage(), start);
        }
    }

    public String lastSql() {
        return lastSql;
    }

    public List<Map<String, Object>> lastRows() {
        return lastRows;
    }

    /** Every query run through this tool, in order, including failures. */
    public List<QueryAttempt> attempts() {
        return List.copyOf(attempts);
    }

    private List<Map<String, Object>> runReadOnly(Connection connection, String query) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (Statement setup = connection.createStatement()) {
                setup.execute("SET TRANSACTION READ ONLY");
                setup.execute("SET LOCAL statement_timeout = " + timeout.toMillis());
            }
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setMaxRows(MAX_ROWS);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return new RowMapperResultSetExtractor<>(new ColumnMapRowMapper()).extractData(resultSet);
                }
            }
        } finally {
            // Nothing a query does is ever kept.
            connection.rollback();
            connection.setAutoCommit(autoCommit);
        }
    }

    private static String withoutTrailingSemicolon(String sql) {
        return sql.endsWith(";") ? sql.substring(0, sql.length() - 1).strip() : sql;
    }

    private Map<String, String> failed(String query, String message, long start) {
        attempts.add(QueryAttempt.failed(query, message, elapsedMillis(start)));
        return Map.of("error", message);
    }

    private static long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    // JSON-friendly values: readable dates instead of epoch numbers, and driver-specific types as text.
    private static Map<String, Object> toPlainValues(Map<String, Object> row) {
        Map<String, Object> plain = new LinkedHashMap<>();
        row.forEach((column, value) -> plain.put(column, switch (value) {
            case null -> null;
            case Timestamp t -> t.toLocalDateTime().toString();
            case Date d -> d.toLocalDate().toString();
            case Time t -> t.toLocalTime().toString();
            case Number n -> n;
            case Boolean b -> b;
            default -> value.toString();
        }));
        return plain;
    }
}
