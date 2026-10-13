package io.github.calegrandy.askdb;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Sends every MockMvc request as a signed-in user, with a test JWT that skips signature checks, so API tests
 * can focus on behaviour. {@code ApiSecurityIntegrationTest} doesn't import this: it tests the sign-in itself.
 */
@TestConfiguration(proxyBeanMethods = false)
public class SignedInUserConfig {

    @Bean
    MockMvcBuilderCustomizer signedInByDefault() {
        return builder -> builder.defaultRequest(get("/").with(jwt()));
    }
}
