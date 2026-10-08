package com.example.logicomposer.query;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The tool Claude calls to run SQL against one database. One instance per question:
 * it remembers the last successful query so the API can return it with the answer.
 */
public class SqlTool {

    public static final int MAX_ROWS = 100;

    private final JdbcTemplate jdbcTemplate;
    private String lastSql;
    private List<Map<String, Object>> lastRows = List.of();

    public SqlTool(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbcTemplate.setMaxRows(MAX_ROWS);
    }

    @Tool(name = "run_sql", description = """
            Runs one PostgreSQL SELECT statement and returns the result rows as JSON. \
            At most 100 rows are returned. On failure, returns the database error message.""")
    public Object runSql(@ToolParam(description = "A single PostgreSQL SELECT statement") String sql) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql).stream()
                    .map(SqlTool::toPlainValues)
                    .toList();
            lastSql = sql;
            lastRows = rows;
            return rows;
        } catch (DataAccessException e) {
            // Returned, not thrown, so the model can read the error and fix its query.
            return Map.of("error", e.getMostSpecificCause().getMessage());
        }
    }

    public String lastSql() {
        return lastSql;
    }

    public List<Map<String, Object>> lastRows() {
        return lastRows;
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
