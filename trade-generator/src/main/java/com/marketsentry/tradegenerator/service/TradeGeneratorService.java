package com.marketsentry.tradegenerator.service;

import com.marketsentry.tradegenerator.model.TradeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeGeneratorService {

    private final KafkaTemplate<String, TradeEvent> kafkaTemplate;

    @Value("${marketsentry.kafka.topics.trade-events}")
    private String tradeEventsTopic;

    private static final List<String> TRADERS = List.of(
            "T1001", "T1002", "T1003", "T1004", "T1005",
            "T2001", "T2002", "T2003", "T2004", "T2005"
    );

    private static final List<String> STOCKS = List.of(
            "AAPL", "GOOGL", "MSFT", "AMZN", "TSLA",
            "META", "NVDA", "JPM", "BAC", "GS"
    );

    private static final List<BigDecimal> BASE_PRICES = List.of(
            new BigDecimal("192.45"), new BigDecimal("175.30"), new BigDecimal("415.20"),
            new BigDecimal("185.60"), new BigDecimal("245.80"), new BigDecimal("520.10"),
            new BigDecimal("875.50"), new BigDecimal("198.40"), new BigDecimal("38.90"),
            new BigDecimal("385.70")
    );

    private final Random random = new Random();
    private final AtomicLong tradeCounter = new AtomicLong(1000);

    @Scheduled(fixedRateString = "${marketsentry.generator.rate-ms:500}")
    public void generateTrade() {
        TradeEvent trade = buildRandomTrade();
        kafkaTemplate.send(tradeEventsTopic, trade.getTraderId(), trade);
        log.debug("Published trade: {} for trader: {}", trade.getTradeId(), trade.getTraderId());
    }

    private TradeEvent buildRandomTrade() {
        int stockIndex = random.nextInt(STOCKS.size());
        BigDecimal basePrice = BASE_PRICES.get(stockIndex);
        BigDecimal priceVariance = basePrice.multiply(new BigDecimal(random.nextDouble() * 0.02 - 0.01));
        BigDecimal finalPrice = basePrice.add(priceVariance).setScale(2, RoundingMode.HALF_UP);

        return TradeEvent.builder()
                .tradeId("TRX" + tradeCounter.incrementAndGet())
                .traderId(TRADERS.get(random.nextInt(TRADERS.size())))
                .stock(STOCKS.get(stockIndex))
                .side(random.nextBoolean() ? TradeEvent.TradeSide.BUY : TradeEvent.TradeSide.SELL)
                .quantity((long) (random.nextInt(990) + 10))
                .price(finalPrice)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
