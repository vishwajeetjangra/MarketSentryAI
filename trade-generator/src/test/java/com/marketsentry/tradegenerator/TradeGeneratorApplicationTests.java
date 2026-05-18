package com.marketsentry.tradegenerator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

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
