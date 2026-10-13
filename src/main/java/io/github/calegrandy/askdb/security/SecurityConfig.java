package io.github.calegrandy.askdb.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Every request needs a valid Cognito access token (validated by Spring Boot's JWT decoder plus
 * {@link CognitoAccessTokenValidator}). The API is stateless: each request carries its own token.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                .oauth2ResourceServer(server -> server.jwt(jwt -> {}))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF attacks ride on cookies the browser sends automatically. This API takes bearer tokens,
                // which a browser never attaches on its own, so CSRF protection doesn't apply.
                .csrf(AbstractHttpConfigurer::disable)
                .build();
    }
}
