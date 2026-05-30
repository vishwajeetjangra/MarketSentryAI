package com.marketsentry.tradegenerator.service;

import com.marketsentry.tradegenerator.model.TradeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Publishes anomalous trading patterns that will trigger surveillance rules.
 *
 * The methods are public and accept an explicit traderId so they can be called
 * from a schedule (ScheduledAnomalyInjector) OR directly from a REST endpoint
 * (GeneratorControlController). Pass null/blank to pick a random anomaly trader.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyInjectorService {

    private final KafkaTemplate<String, TradeEvent> kafkaTemplate;

    @Value("${marketsentry.kafka.topics.trade-events}")
    private String tradeEventsTopic;

    private static final List<String> ANOMALY_TRADERS = List.of("T9001", "T9002", "T9003");
    private static final List<String> STOCKS = List.of("AAPL", "TSLA", "NVDA");

    private final Random random = new Random();
    private final long startInstantMs = System.currentTimeMillis();
    private final AtomicLong counter = new AtomicLong(0);

    /** @return the trader the burst was attributed to */
    public String injectHighFrequencyBurst(String requestedTrader) {
        String traderId = resolveTrader(requestedTrader);
        String stock = STOCKS.get(random.nextInt(STOCKS.size()));
        log.warn("Injecting HIGH_FREQUENCY_SPIKE for trader: {}", traderId);

        // Pure HIGH_FREQUENCY burst: all same-side trades, simulating an aggressive
        // accumulator. Alternating sides would also (incorrectly) fire the
        // RAPID_BUY_SELL_REVERSAL rule, which would contaminate the test signal.
        for (int i = 0; i < 35; i++) {
            TradeEvent trade = TradeEvent.builder()
                    .tradeId(nextId())
                    .traderId(traderId)
                    .stock(stock)
                    .side(TradeEvent.TradeSide.BUY)
                    .quantity(100L)
                    .price(new BigDecimal("200.00"))
                    .timestamp(LocalDateTime.now())
                    .build();
            kafkaTemplate.send(tradeEventsTopic, traderId, trade);
        }
        return traderId;
    }

    public String injectVolumeSpike(String requestedTrader) {
        String traderId = resolveTrader(requestedTrader);
        log.warn("Injecting VOLUME_SPIKE for trader: {}", traderId);

        TradeEvent trade = TradeEvent.builder()
                .tradeId(nextId())
                .traderId(traderId)
                .stock("TSLA")
                .side(TradeEvent.TradeSide.BUY)
                .quantity(50_000L)
                .price(new BigDecimal("245.80"))
                .timestamp(LocalDateTime.now())
                .build();
        kafkaTemplate.send(tradeEventsTopic, traderId, trade);
        return traderId;
    }

    public String injectRapidReversals(String requestedTrader) {
        String traderId = resolveTrader(requestedTrader);
        log.warn("Injecting RAPID_BUY_SELL_REVERSAL for trader: {}", traderId);

        TradeEvent.TradeSide side = TradeEvent.TradeSide.BUY;
        for (int i = 0; i < 8; i++) {
            TradeEvent trade = TradeEvent.builder()
                    .tradeId(nextId())
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
        return traderId;
    }

    private String resolveTrader(String requested) {
        if (requested != null && !requested.isBlank()) {
            return requested;
        }
        return ANOMALY_TRADERS.get(random.nextInt(ANOMALY_TRADERS.size()));
    }

    private String nextId() {
        return "ANO-" + startInstantMs + "-" + counter.incrementAndGet();
    }
}
