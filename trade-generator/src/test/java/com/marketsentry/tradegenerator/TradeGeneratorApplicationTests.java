package com.marketsentry.tradegenerator;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Default Spring Boot context-loading smoke test. Disabled because it needs the
 * full Kafka infra running locally, so it fails in CI and any developer machine
 * that hasn't started docker-compose. Enable on demand when verifying wiring.
 */
@Disabled("Requires local Kafka — run docker-compose up first, then re-enable manually.")
@SpringBootTest
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=localhost:9092",
        "marketsentry.generator.rate-ms=999999"
})
class TradeGeneratorApplicationTests {

    @Test
    void contextLoads() {
    }
}
