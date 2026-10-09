package io.github.calegrandy.askdb.controller;

import com.jayway.jsonpath.JsonPath;
import io.github.calegrandy.askdb.ContainersConfig;
import io.github.calegrandy.askdb.FakeAiConfig;
import io.github.calegrandy.askdb.FakeChatModel;
import io.github.calegrandy.askdb.enums.ConnectionType;
import io.github.calegrandy.askdb.model.Connection;
import io.github.calegrandy.askdb.repository.ConnectionRepository;
import io.github.calegrandy.askdb.repository.QueryHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asks questions through the API with a scripted fake model, and checks what is saved to the query history.
 * The registered connection points at the test's own Postgres container, so run_sql queries real tables.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ContainersConfig.class, FakeAiConfig.class})
class QueryApiIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    FakeChatModel fakeModel;

    @Autowired
    PostgreSQLContainer postgres;

    @Autowired
    ConnectionRepository connectionRepository;

    @Autowired
    QueryHistoryRepository queryHistoryRepository;

    Connection connection;

    @BeforeEach
    void setUp() {
        fakeModel.reset();
        queryHistoryRepository.deleteAll();
        connectionRepository.deleteAll();
        connection = connectionRepository.save(Connection.builder()
                .username(postgres.getUsername())
                .password(postgres.getPassword())
                .url(postgres.getJdbcUrl())
                .type(ConnectionType.POSTGRESQL)
                .build());
    }

    @Test
    void askSavesSuccessfulQueryToHistory() {
        fakeModel.willRunSql("SELECT id, username FROM connection").willAnswer("There is 1 connection.");

        MvcTestResult result = ask("How many connections are there?");

        assertThat(result).hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        { "answer": "There is 1 connection.", "sql": "SELECT id, username FROM connection" }
                        """);
        Long queryId = queryIdOf(result);

        assertThat(mvc.get().uri("/api/queries/{id}", queryId))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "connectionId": %d,
                          "question": "How many connections are there?",
                          "status": "SUCCEEDED",
                          "answer": "There is 1 connection.",
                          "finalSql": "SELECT id, username FROM connection",
                          "rowCount": 1,
                          "attempts": [ { "sql": "SELECT id, username FROM connection", "rowCount": 1 } ],
                          "model": "%s",
                          "inputTokens": %d,
                          "outputTokens": %d
                        }
                        """.formatted(connection.getId(), FakeChatModel.MODEL,
                        FakeChatModel.INPUT_TOKENS, FakeChatModel.OUTPUT_TOKENS));
    }

    @Test
    void historyNeverStoresResultRows() {
        fakeModel.willRunSql("SELECT id, username FROM connection");

        Long queryId = queryIdOf(ask("List the connections"));

        assertThat(mvc.get().uri("/api/queries/{id}", queryId))
                .bodyJson().doesNotHavePath("$.rows");
    }

    @Test
    void historyRecordsFailedAttemptsBeforeTheCorrection() {
        fakeModel.willRunSql("SELECT no_such_column FROM connection", "SELECT id FROM connection");

        Long queryId = queryIdOf(ask("List the connection ids"));

        assertThat(mvc.get().uri("/api/queries/{id}", queryId))
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.attempts.length()").isEqualTo(2);
                    assertThat(json).extractingPath("$.attempts[0].error").asString().contains("no_such_column");
                    assertThat(json).extractingPath("$.attempts[1].rowCount").isEqualTo(1);
                    assertThat(json).extractingPath("$.finalSql").isEqualTo("SELECT id FROM connection");
                });
    }

    @Test
    void modelFailureReturns502AndIsSavedToHistory() {
        fakeModel.willFail(new RuntimeException("Model API unavailable"));

        assertThat(ask("Anything?"))
                .hasStatus(HttpStatus.BAD_GATEWAY)
                .bodyJson()
                .extractingPath("$.message").asString().contains("Model API unavailable");

        assertThat(queryHistoryRepository.findAll())
                .singleElement()
                .satisfies(history -> {
                    assertThat(history.getStatus().name()).isEqualTo("FAILED");
                    assertThat(history.getErrorMessage()).isEqualTo("Model API unavailable");
                    assertThat(history.getAnswer()).isNull();
                });
    }

    @Test
    void historyListIsNewestFirstAndPaginated() {
        assertThat(ask("First question")).hasStatusOk();
        assertThat(ask("Second question")).hasStatusOk();
        assertThat(ask("Third question")).hasStatusOk();

        assertThat(mvc.get().uri("/api/connections/{id}/queries?size=2", connection.getId()))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "content": [ { "question": "Third question" }, { "question": "Second question" } ],
                          "page": { "size": 2, "number": 0, "totalElements": 3, "totalPages": 2 }
                        }
                        """);
    }

    @Test
    void historyForUnknownConnectionReturns404() {
        assertThat(mvc.get().uri("/api/connections/{id}/queries", 999_999))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void unknownQueryReturns404() {
        assertThat(mvc.get().uri("/api/queries/{id}", 999_999))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("No query found with id: 999999");
    }

    @Test
    void historyIsKeptWhenConnectionIsDeleted() {
        Long queryId = queryIdOf(ask("Anything?"));

        assertThat(mvc.delete().uri("/api/connections/{id}", connection.getId())).hasStatusOk();

        assertThat(mvc.get().uri("/api/queries/{id}", queryId))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.connectionId").isNull();
                    assertThat(json).extractingPath("$.question").isEqualTo("Anything?");
                });
    }

    private MvcTestResult ask(String question) {
        return mvc.post().uri("/api/connections/{id}/queries", connection.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "question": "%s" }
                        """.formatted(question))
                .exchange();
    }

    private static Long queryIdOf(MvcTestResult result) {
        assertThat(result).hasStatusOk();
        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        Number id = JsonPath.read(body, "$.queryId");
        return id.longValue();
    }
}
