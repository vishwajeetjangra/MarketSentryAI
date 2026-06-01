package com.marketsentry.surveillanceengine.service;

import com.marketsentry.surveillanceengine.model.TradeEvent;
import com.marketsentry.surveillanceengine.model.TraderState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that exercise RedisStateManager against a real Redis instance
 * via Testcontainers. We wire up the templates by hand rather than booting the full
 * Spring context — this isolates Redis behavior from Kafka, JPA, and the rest of the app.
 *
 * disabledWithoutDocker = true → JUnit cleanly skips this class with a clear message
 * when the Docker daemon isn't reachable, instead of failing the build. Matches the
 * skip policy used for the default Spring smoke tests.
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisStateManagerIntegrationTest {

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static RedisStateManager manager;
    private static StringRedisTemplate stringTemplate;

    @BeforeAll
    static void initRedis() {
        RedisStandaloneConfiguration cfg =
                new RedisStandaloneConfiguration(redis.getHost(), redis.getFirstMappedPort());
        connectionFactory = new LettuceConnectionFactory(cfg);
        connectionFactory.afterPropertiesSet();

        stringTemplate = new StringRedisTemplate(connectionFactory);

        RedisTemplate<String, TraderState> stateTemplate = new RedisTemplate<>();
        stateTemplate.setConnectionFactory(connectionFactory);
        stateTemplate.setKeySerializer(new StringRedisSerializer());
        stateTemplate.setValueSerializer(new Jackson2JsonRedisSerializer<>(TraderState.class));
        stateTemplate.setHashKeySerializer(new StringRedisSerializer());
        stateTemplate.afterPropertiesSet();

        manager = new RedisStateManager(stateTemplate, stringTemplate);
    }

    @AfterAll
    static void shutdownRedis() {
        if (connectionFactory != null) connectionFactory.destroy();
    }

    @BeforeEach
    void flushBetweenTests() {
        // Wipe every key so each test starts from a clean state
        stringTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) conn -> {
            conn.serverCommands().flushAll();
            return null;
        });
    }

    // ---------- claimTrade ----------

    @Test
    void claimTradeReturnsTrueFirstTimeAndFalseAfterwards() {
        String tradeId = "TR-" + UUID.randomUUID();

        assertThat(manager.claimTrade(tradeId)).isTrue();
        assertThat(manager.claimTrade(tradeId)).isFalse();
        assertThat(manager.claimTrade(tradeId)).isFalse();
    }

    @Test
    void claimTradeIsScopedPerTradeId() {
        assertThat(manager.claimTrade("TR-A")).isTrue();
        assertThat(manager.claimTrade("TR-B")).isTrue();   // different id → independent claim
        assertThat(manager.claimTrade("TR-A")).isFalse();  // re-claim still blocked
    }

    // ---------- trade-rate sliding window ----------

    @Test
    void tradesInWindowReturnsZeroForUnknownTrader() {
        assertThat(manager.getTradesInWindow("never-traded")).isZero();
    }

    @Test
    void tradesInWindowCountsRecentTradesAfterUpdate() {
        TraderState state = manager.getOrCreate("trader-1");
        for (int i = 0; i < 5; i++) {
            manager.update(state, trade("trader-1", 100, TradeEvent.TradeSide.BUY));
        }
        assertThat(manager.getTradesInWindow("trader-1")).isEqualTo(5);
    }

    // ---------- reversal sliding window ----------

    @Test
    void reversalsInWindowIsZeroForSameSideTrades() {
        TraderState state = manager.getOrCreate("trader-2");
        for (int i = 0; i < 5; i++) {
            manager.update(state, trade("trader-2", 100, TradeEvent.TradeSide.BUY));
        }
        assertThat(manager.getReversalsInWindow("trader-2")).isZero();
    }

    @Test
    void reversalsInWindowCountsEverySideFlip() {
        TraderState state = manager.getOrCreate("trader-3");

        // BUY, SELL, BUY, SELL, BUY → 4 reversals (first trade has no prior side)
        manager.update(state, trade("trader-3", 100, TradeEvent.TradeSide.BUY));
        manager.update(state, trade("trader-3", 100, TradeEvent.TradeSide.SELL));
        manager.update(state, trade("trader-3", 100, TradeEvent.TradeSide.BUY));
        manager.update(state, trade("trader-3", 100, TradeEvent.TradeSide.SELL));
        manager.update(state, trade("trader-3", 100, TradeEvent.TradeSide.BUY));

        assertThat(manager.getReversalsInWindow("trader-3")).isEqualTo(4);
    }

    // ---------- EMA evolution ----------

    @Test
    void firstTradeSeedsEmaWithThatVolume() {
        TraderState state = manager.getOrCreate("trader-4");
        manager.update(state, trade("trader-4", 500, TradeEvent.TradeSide.BUY));
        assertThat(state.getAvgTradeVolume()).isEqualByComparingTo(new BigDecimal("500"));
    }

    @Test
    void emaConvergesTowardSteadyStream() {
        TraderState state = manager.getOrCreate("trader-5");

        manager.update(state, trade("trader-5", 100, TradeEvent.TradeSide.BUY));
        for (int i = 0; i < 10; i++) {
            manager.update(state, trade("trader-5", 100, TradeEvent.TradeSide.BUY));
        }
        // Steady stream of 100 → EMA stays at 100.
        assertThat(state.getAvgTradeVolume()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void emaShiftsToward500ButResistsSingleSpike() {
        TraderState state = manager.getOrCreate("trader-6");
        manager.update(state, trade("trader-6", 100, TradeEvent.TradeSide.BUY));    // seed → 100
        manager.update(state, trade("trader-6", 500, TradeEvent.TradeSide.BUY));    // 0.9*100 + 0.1*500 = 140

        assertThat(state.getAvgTradeVolume()).isEqualByComparingTo(new BigDecimal("140.00"));
    }

    // ---------- helpers ----------

    private TradeEvent trade(String traderId, long quantity, TradeEvent.TradeSide side) {
        return TradeEvent.builder()
                .tradeId("T-" + UUID.randomUUID())
                .traderId(traderId)
                .stock("AAPL")
                .side(side)
                .quantity(quantity)
                .price(new BigDecimal("100.00"))
                .timestamp(LocalDateTime.now())
                .build();
    }
}
