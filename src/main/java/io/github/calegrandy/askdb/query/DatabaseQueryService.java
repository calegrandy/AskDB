package io.github.calegrandy.askdb.query;

import io.github.calegrandy.askdb.dto.QueryResponse;
import io.github.calegrandy.askdb.enums.QueryStatus;
import io.github.calegrandy.askdb.exception.QueryFailedException;
import io.github.calegrandy.askdb.model.Connection;
import io.github.calegrandy.askdb.model.QueryHistory;
import io.github.calegrandy.askdb.repository.QueryHistoryRepository;
import io.github.calegrandy.askdb.service.ConnectionService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;

/**
 * Answers a plain-English question about a registered database: gives Claude the schema and a SQL tool,
 * lets it query until it can answer, and returns the answer with the SQL and rows it was based on.
 * Every question is saved to the query history, whether it succeeds or fails.
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
    private final QueryHistoryRepository queryHistoryRepository;
    private final ChatClient chatClient;
    private final String configuredModel;

    public DatabaseQueryService(ConnectionService connectionService,
                                QueryHistoryRepository queryHistoryRepository,
                                ChatClient.Builder chatClientBuilder,
                                @Value("${spring.ai.anthropic.chat.options.model}") String configuredModel) {
        this.connectionService = connectionService;
        this.queryHistoryRepository = queryHistoryRepository;
        this.chatClient = chatClientBuilder.build();
        this.configuredModel = configuredModel;
    }

    public QueryResponse ask(Long connectionId, String question) {
        Connection connection = connectionService.getConnection(connectionId);

        long start = System.nanoTime();
        QueryHistory history = QueryHistory.builder()
                .connectionId(connectionId)
                .question(question)
                .model(configuredModel)
                .createdAt(Instant.now())
                .build();
        SqlTool sqlTool = null;

        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
                    connection.getUrl(), connection.getUsername(), connection.getPassword()));
            sqlTool = new SqlTool(jdbcTemplate);

            ChatResponse response = chatClient.prompt()
                    .system(SYSTEM_PROMPT.formatted(SchemaReader.describe(jdbcTemplate)))
                    .user(question)
                    .tools(sqlTool)
                    .call()
                    .chatResponse();

            recordSuccess(history, response, sqlTool);
            save(history, sqlTool, start);
            return new QueryResponse(history.getId(), history.getAnswer(), sqlTool.lastSql(), sqlTool.lastRows());
        } catch (RuntimeException e) {
            // Failures are saved too: they're the most useful entries for debugging and evals.
            history.setStatus(QueryStatus.FAILED);
            history.setErrorMessage(e.getMessage());
            save(history, sqlTool, start);
            throw new QueryFailedException(history.getId(), e);
        }
    }

    private static void recordSuccess(QueryHistory history, ChatResponse response, SqlTool sqlTool) {
        history.setStatus(QueryStatus.SUCCEEDED);
        history.setAnswer(response.getResult().getOutput().getText());
        history.setFinalSql(sqlTool.lastSql());
        history.setRowCount(sqlTool.lastSql() == null ? null : sqlTool.lastRows().size());

        // Spring AI adds up token usage across all the model calls in the tool loop.
        Usage usage = response.getMetadata().getUsage();
        history.setInputTokens(usage.getPromptTokens());
        history.setOutputTokens(usage.getCompletionTokens());
        if (StringUtils.hasText(response.getMetadata().getModel())) {
            history.setModel(response.getMetadata().getModel());
        }
    }

    private void save(QueryHistory history, SqlTool sqlTool, long startNanos) {
        if (sqlTool != null) {
            history.setAttempts(sqlTool.attempts());
        }
        history.setDurationMs(Duration.ofNanos(System.nanoTime() - startNanos).toMillis());
        queryHistoryRepository.save(history);
    }
}
