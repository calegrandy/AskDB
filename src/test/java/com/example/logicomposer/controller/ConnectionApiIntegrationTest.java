package com.example.logicomposer.controller;

import com.example.logicomposer.ContainersConfig;
import com.example.logicomposer.enums.ConnectionType;
import com.example.logicomposer.model.Connection;
import com.example.logicomposer.repository.ConnectionRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the whole application against a real Postgres container and sends
 * HTTP requests through MockMvc: controller -> service -> repository -> database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
class ConnectionApiIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    ConnectionRepository repository;

    // The container (and its data) is shared by every test in this class,
    // so start each test from an empty table.
    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void createReturns201AndSavesToDatabase() {
        assertThat(mvc.post().uri("/api/connections")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "username": "alice",
                          "password": "secret",
                          "url": "jdbc:postgresql://localhost:5432/app",
                          "type": "postgresql"
                        }
                        """))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.username").isEqualTo("alice");

        // Check the database directly, not just the response.
        assertThat(repository.findAll())
                .singleElement()
                .extracting(Connection::getUsername)
                .isEqualTo("alice");
    }

    @Test
    void createReturnsLocationOfNewConnection() {
        MvcTestResult result = mvc.post().uri("/api/connections")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "username": "liam",
                          "password": "secret",
                          "url": "jdbc:postgresql://localhost:5432/app",
                          "type": "postgresql"
                        }
                        """)
                .exchange();

        Long id = repository.findAll().getFirst().getId();
        assertThat(result)
                .hasStatus(HttpStatus.CREATED)
                .headers().hasValue("Location", "http://localhost/api/connections/" + id);

        // Following the Location header must lead to the new connection.
        assertThat(mvc.get().uri(result.getResponse().getHeader("Location")))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.username").isEqualTo("liam");
    }

    @Test
    void createWithBlankUsernameReturns400AndSavesNothing() {
        assertThat(mvc.post().uri("/api/connections")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "username": "",
                          "password": "secret",
                          "url": "jdbc:postgresql://localhost:5432/app",
                          "type": "postgresql"
                        }
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST);

        assertThat(repository.count()).isZero();
    }

    @Test
    void getReturnsExistingConnection() {
        Connection saved = repository.save(newConnection("bob"));

        assertThat(mvc.get().uri("/api/connections/{id}", saved.getId()))
                .hasStatusOk()
                .bodyJson()
                // "Lenient" ignores fields not listed here (id, url).
                // Jackson 3 writes enums using toString(), so type comes back lowercase.
                .isLenientlyEqualTo("""
                        { "username": "bob", "type": "postgresql" }
                        """);
    }

    @Test
    void getUnknownIdReturns404WithMessage() {
        assertThat(mvc.get().uri("/api/connections/{id}", 999_999))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("No connection found with id: 999999");
    }

    @Test
    void getAllReturnsEveryConnection() {
        repository.save(newConnection("alice"));
        repository.save(newConnection("bob"));

        assertThat(mvc.get().uri("/api/connections"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].username").asArray().containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void patchUpdatesOnlyTheGivenField() {
        Connection saved = repository.save(newConnection("carol"));

        assertThat(mvc.patch().uri("/api/connections/{id}", saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "url": "jdbc:postgresql://db.internal:5432/app" }
                        """))
                .hasStatusOk();

        Connection updated = repository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getUrl()).isEqualTo("jdbc:postgresql://db.internal:5432/app");
        assertThat(updated.getUsername()).isEqualTo("carol");
    }

    @Test
    void patchUnknownFieldReturns400() {
        Connection saved = repository.save(newConnection("dave"));

        assertThat(mvc.patch().uri("/api/connections/{id}", saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "colour": "blue" }
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("Unknown field: colour");
    }

    @Test
    void putReplacesConnectionAtUrlIdAndIgnoresIdInBody() {
        Connection target = repository.save(newConnection("henry"));
        Connection other = repository.save(newConnection("iris"));

        assertThat(mvc.put().uri("/api/connections/{id}", target.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "id": %d,
                          "username": "henry2",
                          "password": "new-secret",
                          "url": "jdbc:postgresql://db.internal:5432/app",
                          "type": "postgresql"
                        }
                        """.formatted(other.getId())))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        { "id": %d, "username": "henry2" }
                        """.formatted(target.getId()));

        Connection updated = repository.findById(target.getId()).orElseThrow();
        assertThat(updated.getUsername()).isEqualTo("henry2");
        assertThat(updated.getPassword()).isEqualTo("new-secret");
        assertThat(updated.getUrl()).isEqualTo("jdbc:postgresql://db.internal:5432/app");

        // The connection whose id was in the body must be untouched.
        assertThat(repository.findById(other.getId()).orElseThrow().getUsername()).isEqualTo("iris");
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void putUnknownIdReturns404AndCreatesNothing() {
        assertThat(mvc.put().uri("/api/connections/{id}", 999_999)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "username": "jack",
                          "password": "secret",
                          "url": "jdbc:postgresql://localhost:5432/app",
                          "type": "postgresql"
                        }
                        """))
                .hasStatus(HttpStatus.NOT_FOUND);

        assertThat(repository.count()).isZero();
    }

    @Test
    void putWithMissingFieldReturns400AndChangesNothing() {
        Connection saved = repository.save(newConnection("kate"));

        assertThat(mvc.put().uri("/api/connections/{id}", saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "username": "kate2" }
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST);

        assertThat(repository.findById(saved.getId()).orElseThrow().getUsername()).isEqualTo("kate");
    }

    @Test
    void deleteRemovesConnection() {
        Connection saved = repository.save(newConnection("erin"));

        assertThat(mvc.delete().uri("/api/connections/{id}", saved.getId()))
                .hasStatusOk();

        assertThat(repository.existsById(saved.getId())).isFalse();
    }

    @Test
    void deleteUnknownIdReturns404() {
        assertThat(mvc.delete().uri("/api/connections/{id}", 999_999))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("No connection found with id: 999999");
    }

    @Test
    void responsesNeverIncludePassword() {
        Connection saved = repository.save(newConnection("frank"));

        assertThat(mvc.post().uri("/api/connections")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "username": "grace",
                          "password": "secret",
                          "url": "jdbc:postgresql://localhost:5432/app",
                          "type": "postgresql"
                        }
                        """))
                .bodyJson().doesNotHavePath("$.password");

        assertThat(mvc.get().uri("/api/connections/{id}", saved.getId()))
                .bodyJson().doesNotHavePath("$.password");

        assertThat(mvc.get().uri("/api/connections"))
                .bodyJson().doesNotHavePath("$[0].password");

        assertThat(mvc.patch().uri("/api/connections/{id}", saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "username": "frank2" }
                        """))
                .bodyJson().doesNotHavePath("$.password");
    }

    private static Connection newConnection(String username) {
        return Connection.builder()
                .username(username)
                .password("secret")
                .url("jdbc:postgresql://localhost:5432/app")
                .type(ConnectionType.POSTGRESQL)
                .build();
    }
}
