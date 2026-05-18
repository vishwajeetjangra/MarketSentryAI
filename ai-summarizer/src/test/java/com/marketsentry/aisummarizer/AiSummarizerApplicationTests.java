package com.marketsentry.aisummarizer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.datasource.url=jdbc:postgresql://localhost:5432/marketsentry"
})
class AiSummarizerApplicationTests {

    @Test
    void contextLoads() {
    }
}
