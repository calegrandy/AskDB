package io.github.calegrandy.askdb;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces Claude with {@link FakeChatModel}, so tests never call the real API. */
@TestConfiguration(proxyBeanMethods = false)
public class FakeAiConfig {

    @Bean
    @Primary
    FakeChatModel fakeChatModel() {
        return new FakeChatModel();
    }
}
