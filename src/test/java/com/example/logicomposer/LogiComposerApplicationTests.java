package com.example.logicomposer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(ContainersConfig.class)
class LogiComposerApplicationTests {

    @Test
    void contextLoads() {
    }
}