package com.marketsentry.tradegenerator.service;

import com.marketsentry.tradegenerator.model.TradeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeGeneratorService {

    private final KafkaTemplate<String, TradeEvent> kafkaTemplate;
    private final TaskScheduler taskScheduler;

    @Value("${marketsentry.kafka.topics.trade-events}")
    private String tradeEventsTopic;

    @Value("${marketsentry.generator.rate-ms:500}")
    private long initialRateMs;

    @Value("${marketsentry.generator.autostart:true}")
    private boolean autostart;

    private static final List<String> TRADERS = List.of(
            "T1001", "T1002", "T1003", "T1004", "T1005",
            "T2001", "T2002", "T2003", "T2004", "T2005"
    );

    private static final List<String> STOCKS = List.of(
            "AAPL", "GOOGL", "MSFT", "AMZN", "TSLA",
            "META", "NVDA", "JPM", "BAC", "GS"
    );

    // Starting prices — each stock walks away from here over time
    private static final Map<String, BigDecimal> INITIAL_PRICES = Map.ofEntries(
            Map.entry("AAPL", new BigDecimal("192.45")),
            Map.entry("GOOGL", new BigDecimal("175.30")),
            Map.entry("MSFT", new BigDecimal("415.20")),
            Map.entry("AMZN", new BigDecimal("185.60")),
            Map.entry("TSLA", new BigDecimal("245.80")),
            Map.entry("META", new BigDecimal("520.10")),
            Map.entry("NVDA", new BigDecimal("875.50")),
            Map.entry("JPM", new BigDecimal("198.40")),
            Map.entry("BAC", new BigDecimal("38.90")),
            Map.entry("GS", new BigDecimal("385.70"))
    );

    // Live price per stock — mutated on every trade so it has memory (random walk)
    private final Map<String, BigDecimal> currentPrices = new ConcurrentHashMap<>(INITIAL_PRICES);

    // 0.2% per-step volatility; Gaussian so most moves are tiny, a few are larger
    private static final double DRIFT_STDDEV = 0.002;
    private static final BigDecimal PRICE_FLOOR = new BigDecimal("1.00");

    private final Random random = new Random();
    private final AtomicLong tradeCounter = new AtomicLong(1000);

    // Runtime-controllable scheduling state
    private volatile long rateMs;
    private volatile ScheduledFuture<?> scheduledTask;

    /**
     * Runs once the whole context (including Kafka) is ready — safer than
     * @PostConstruct, which fires before the app is fully wired.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        this.rateMs = initialRateMs;
        if (autostart) {
            start();
        } else {
            log.info("Trade generator autostart disabled — waiting for POST /generator/start");
        }
    }

    /** @return true if it actually started, false if it was already running */
    public synchronized boolean start() {
        if (isRunning()) {
            return false;
        }
        scheduledTask = taskScheduler.scheduleAtFixedRate(this::generateTrade, Duration.ofMillis(rateMs));
        log.info("Trade generator STARTED at {} ms/trade", rateMs);
        return true;
    }

    /** @return true if it actually stopped, false if it was already stopped */
    public synchronized boolean stop() {
        if (!isRunning()) {
            return false;
        }
        scheduledTask.cancel(false);   // false = let an in-flight trade finish
        scheduledTask = null;
        log.info("Trade generator STOPPED");
        return true;
    }

    /**
     * You can't mutate a live schedule's period — so to change the rate we
     * cancel the current schedule and create a fresh one at the new rate.
     */
    public synchronized void setRate(long ms) {
        if (ms < 1) {
            throw new IllegalArgumentException("rate must be >= 1 ms");
        }
        this.rateMs = ms;
        if (isRunning()) {
            stop();
            start();
        }
        log.info("Trade generator rate set to {} ms/trade", ms);
    }

    public boolean isRunning() {
        return scheduledTask != null && !scheduledTask.isCancelled();
    }

    public Map<String, Object> status() {
        return Map.of(
                "running", isRunning(),
                "rateMs", rateMs,
                "tradesProduced", tradeCounter.get() - 1000,
                "trackedStocks", currentPrices.size()
        );
    }

    public void generateTrade() {
        TradeEvent trade = buildRandomTrade();
        kafkaTemplate.send(tradeEventsTopic, trade.getTraderId(), trade);
        log.debug("Published trade: {} for trader: {}", trade.getTradeId(), trade.getTraderId());
    }

    private TradeEvent buildRandomTrade() {
        String stock = STOCKS.get(random.nextInt(STOCKS.size()));
        BigDecimal price = nextPrice(stock);

        return TradeEvent.builder()
                .tradeId("TRX" + tradeCounter.incrementAndGet())
                .traderId(TRADERS.get(random.nextInt(TRADERS.size())))
                .stock(stock)
                .side(random.nextBoolean() ? TradeEvent.TradeSide.BUY : TradeEvent.TradeSide.SELL)
                .quantity((long) (random.nextInt(990) + 10))
                .price(price)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Advances a stock's price by one random-walk step and returns the new price.
     * compute() makes the read-modify-write atomic so concurrent scheduler
     * threads can't clobber each other's price update.
     */
    private BigDecimal nextPrice(String stock) {
        return currentPrices.compute(stock, (s, prev) -> {
            double pctMove = random.nextGaussian() * DRIFT_STDDEV;
            BigDecimal moved = prev
                    .multiply(BigDecimal.valueOf(1.0 + pctMove))
                    .setScale(2, RoundingMode.HALF_UP);
            return moved.max(PRICE_FLOOR);
        });
    }
}
