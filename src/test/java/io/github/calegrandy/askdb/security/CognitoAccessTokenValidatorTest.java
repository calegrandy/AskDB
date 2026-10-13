package io.github.calegrandy.askdb.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CognitoAccessTokenValidatorTest {

    private final CognitoAccessTokenValidator validator = new CognitoAccessTokenValidator("askdb-client");

    @Test
    void acceptsAnAccessTokenIssuedToAskDb() {
        assertThat(validator.validate(token(Map.of("token_use", "access", "client_id", "askdb-client"))).hasErrors())
                .isFalse();
    }

    @Test
    void rejectsAnIdToken() {
        // ID tokens prove who signed in to the client app; they aren't meant to authorize API calls.
        assertThat(validator.validate(token(Map.of("token_use", "id", "client_id", "askdb-client"))).hasErrors())
                .isTrue();
    }

    @Test
    void rejectsATokenIssuedToAnotherAppInTheSameUserPool() {
        assertThat(validator.validate(token(Map.of("token_use", "access", "client_id", "other-app"))).hasErrors())
                .isTrue();
    }

    @Test
    void rejectsATokenWithoutTheClaims() {
        assertThat(validator.validate(token(Map.of("sub", "someone"))).hasErrors()).isTrue();
    }

    private static Jwt token(Map<String, Object> claims) {
        return Jwt.withTokenValue("token").header("alg", "RS256").claims(c -> c.putAll(claims)).build();
    }
}
