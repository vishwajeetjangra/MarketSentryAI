package com.marketsentry.surveillanceengine;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Default Spring Boot context-loading smoke test. Disabled because it needs Kafka,
 * Postgres, and Redis running locally; it fails in CI and on any machine without
 * docker-compose up. The meaningful Redis-backed integration test lives in
 * service/RedisStateManagerIntegrationTest (Testcontainers-driven, self-contained).
 */
@Disabled("Requires local Kafka, Postgres, Redis — run docker-compose up first, then re-enable manually.")
@SpringBootTest
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.datasource.url=jdbc:postgresql://localhost:5432/marketsentry",
        "spring.data.redis.host=localhost"
})
class SurveillanceEngineApplicationTests {

    @Test
    void contextLoads() {
    }
}
