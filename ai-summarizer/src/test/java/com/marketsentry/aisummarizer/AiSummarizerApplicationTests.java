package com.marketsentry.aisummarizer;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Default Spring Boot context-loading smoke test. Disabled because it needs Kafka,
 * Postgres, and Redis running locally; it fails on any machine without
 * docker-compose up. Service-level logic is covered by OllamaClientTest and
 * SummaryServiceTest, which run with no infra at all.
 */
@Disabled("Requires local Kafka, Postgres, Redis — run docker-compose up first, then re-enable manually.")
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
