package io.github.calegrandy.askdb.controller;

import io.github.calegrandy.askdb.ContainersConfig;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/** Every API endpoint requires a valid bearer token. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
class ApiSecurityIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void requestWithoutTokenReturns401() {
        MvcTestResult result = mvc.get().uri("/api/connections").exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        // Tells the client which kind of token to send (plus a link to the resource's OAuth metadata)
        assertThat(result.getResponse().getHeader("WWW-Authenticate")).startsWith("Bearer");
        assertThat(mvc.get().uri("/api/queries/{id}", 1)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void writeWithoutTokenReturns401AndNot403() {
        // A 403 here would mean CSRF protection is on, which doesn't apply to an API that uses bearer tokens.
        assertThat(mvc.post().uri("/api/connections")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void requestWithTokenIsAllowed() {
        assertThat(mvc.get().uri("/api/connections").with(jwt())).hasStatusOk();
    }

    @Test
    void writeWithTokenIsAllowedWithoutCsrfToken() {
        assertThat(mvc.post().uri("/api/connections").with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "username": "alice",
                          "password": "secret",
                          "url": "jdbc:postgresql://localhost:5432/app",
                          "type": "postgresql"
                        }
                        """))
                .hasStatus(HttpStatus.CREATED);
    }
}
