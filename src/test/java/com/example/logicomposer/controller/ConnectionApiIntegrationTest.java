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
        assertThat(mvc.post().uri("/connections")
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
    void createWithBlankUsernameReturns400AndSavesNothing() {
        assertThat(mvc.post().uri("/connections")
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

        assertThat(mvc.get().uri("/connections/{id}", saved.getId()))
                .hasStatusOk()
                .bodyJson()
                // "Lenient" ignores fields not listed here (id, password, url).
                // Jackson 3 writes enums using toString(), so type comes back lowercase.
                .isLenientlyEqualTo("""
                        { "username": "bob", "type": "postgresql" }
                        """);
    }

    @Test
    void getUnknownIdReturns404WithMessage() {
        assertThat(mvc.get().uri("/connections/{id}", 999_999))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("No connection found with id: 999999");
    }

    @Test
    void getAllReturnsEveryConnection() {
        repository.save(newConnection("alice"));
        repository.save(newConnection("bob"));

        assertThat(mvc.get().uri("/connections"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].username").asArray().containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void patchUpdatesOnlyTheGivenField() {
        Connection saved = repository.save(newConnection("carol"));

        assertThat(mvc.patch().uri("/connections/{id}", saved.getId())
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

        assertThat(mvc.patch().uri("/connections/{id}", saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "colour": "blue" }
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("Unknown field: colour");
    }

    @Test
    void deleteRemovesConnection() {
        Connection saved = repository.save(newConnection("erin"));

        assertThat(mvc.delete().uri("/connections/{id}", saved.getId()))
                .hasStatusOk();

        assertThat(repository.existsById(saved.getId())).isFalse();
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
