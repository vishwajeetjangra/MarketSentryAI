package com.marketsentry.tradegenerator.service;

import com.marketsentry.tradegenerator.model.TradeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Periodically injects anomalous trading patterns to trigger surveillance rules.
 * Enabled via marketsentry.generator.anomaly-injection.enabled=true.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "marketsentry.generator.anomaly-injection.enabled", havingValue = "true")
public class AnomalyInjectorService {

    private final KafkaTemplate<String, TradeEvent> kafkaTemplate;

    @Value("${marketsentry.kafka.topics.trade-events}")
    private String tradeEventsTopic;

    private static final List<String> ANOMALY_TRADERS = List.of("T9001", "T9002", "T9003");
    private static final List<String> STOCKS = List.of("AAPL", "TSLA", "NVDA");

    private final Random random = new Random();
    private final AtomicLong counter = new AtomicLong(9000);

    /** Inject a high-frequency burst — fires every 60 seconds */
    @Scheduled(fixedRateString = "${marketsentry.generator.anomaly-injection.burst-rate-ms:60000}")
    public void injectHighFrequencyBurst() {
        String traderId = ANOMALY_TRADERS.get(random.nextInt(ANOMALY_TRADERS.size()));
        String stock = STOCKS.get(random.nextInt(STOCKS.size()));
        log.warn("Injecting HIGH_FREQUENCY_SPIKE for trader: {}", traderId);

        for (int i = 0; i < 35; i++) {
            TradeEvent trade = TradeEvent.builder()
                    .tradeId("ANO" + counter.incrementAndGet())
                    .traderId(traderId)
                    .stock(stock)
                    .side(i % 2 == 0 ? TradeEvent.TradeSide.BUY : TradeEvent.TradeSide.SELL)
                    .quantity(100L)
                    .price(new BigDecimal("200.00"))
                    .timestamp(LocalDateTime.now())
                    .build();
            kafkaTemplate.send(tradeEventsTopic, traderId, trade);
        }
    }

    /** Inject a volume spike — fires every 90 seconds */
    @Scheduled(fixedRateString = "${marketsentry.generator.anomaly-injection.volume-rate-ms:90000}")
    public void injectVolumeSpike() {
        String traderId = ANOMALY_TRADERS.get(random.nextInt(ANOMALY_TRADERS.size()));
        log.warn("Injecting VOLUME_SPIKE for trader: {}", traderId);

        TradeEvent trade = TradeEvent.builder()
                .tradeId("ANO" + counter.incrementAndGet())
                .traderId(traderId)
                .stock("TSLA")
                .side(TradeEvent.TradeSide.BUY)
                .quantity(50_000L)
                .price(new BigDecimal("245.80"))
                .timestamp(LocalDateTime.now())
                .build();
        kafkaTemplate.send(tradeEventsTopic, traderId, trade);
    }

    /** Inject rapid buy/sell reversals — fires every 75 seconds */
    @Scheduled(fixedRateString = "${marketsentry.generator.anomaly-injection.reversal-rate-ms:75000}")
    public void injectRapidReversals() {
        String traderId = ANOMALY_TRADERS.get(random.nextInt(ANOMALY_TRADERS.size()));
        log.warn("Injecting RAPID_BUY_SELL_REVERSAL for trader: {}", traderId);

        TradeEvent.TradeSide side = TradeEvent.TradeSide.BUY;
        for (int i = 0; i < 8; i++) {
            TradeEvent trade = TradeEvent.builder()
                    .tradeId("ANO" + counter.incrementAndGet())
                    .traderId(traderId)
                    .stock("AAPL")
                    .side(side)
                    .quantity(200L)
                    .price(new BigDecimal("192.50"))
                    .timestamp(LocalDateTime.now())
                    .build();
            kafkaTemplate.send(tradeEventsTopic, traderId, trade);
            side = (side == TradeEvent.TradeSide.BUY) ? TradeEvent.TradeSide.SELL : TradeEvent.TradeSide.BUY;
        }
    }
}
