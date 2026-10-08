package io.github.calegrandy.askdb.query;

import io.github.calegrandy.askdb.dto.QueryResponse;
import io.github.calegrandy.askdb.model.Connection;
import io.github.calegrandy.askdb.service.ConnectionService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

/**
 * Answers a plain-English question about a registered database: gives Claude the schema and a SQL tool,
 * lets it query until it can answer, and returns the answer with the SQL and rows it was based on.
 */
@Service
public class DatabaseQueryService {

    private static final String SYSTEM_PROMPT = """
            You answer questions about a PostgreSQL database by querying it with the run_sql tool.
            - Use only the tables and columns in the schema below.
            - Send one SELECT statement per tool call.
            - If a query fails, read the error, fix the query and try again.
            - Base your answer only on query results. If the data can't answer the question, say so.
            - Answer in a few plain-English sentences.

            Schema (one table per line):
            %s
            """;

    private final ConnectionService connectionService;
    private final ChatClient chatClient;

    public DatabaseQueryService(ConnectionService connectionService, ChatClient.Builder chatClientBuilder) {
        this.connectionService = connectionService;
        this.chatClient = chatClientBuilder.build();
    }

    public QueryResponse ask(Long connectionId, String question) {
        Connection connection = connectionService.getConnection(connectionId);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
                connection.getUrl(), connection.getUsername(), connection.getPassword()));

        SqlTool sqlTool = new SqlTool(jdbcTemplate);
        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT.formatted(SchemaReader.describe(jdbcTemplate)))
                .user(question)
                .tools(sqlTool)
                .call()
                .content();

        return new QueryResponse(answer, sqlTool.lastSql(), sqlTool.lastRows());
    }
}
