package com.marketsentry.surveillanceengine.processor;

import com.marketsentry.surveillanceengine.model.Alert;
import com.marketsentry.surveillanceengine.model.Trade;
import com.marketsentry.surveillanceengine.model.TradeEvent;
import com.marketsentry.surveillanceengine.model.TraderState;
import com.marketsentry.surveillanceengine.repository.TradeRepository;
import com.marketsentry.surveillanceengine.rules.RuleEngine;
import com.marketsentry.surveillanceengine.service.AlertService;
import com.marketsentry.surveillanceengine.service.RedisStateManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class TradeProcessor {

    private final RedisStateManager redisStateManager;
    private final RuleEngine ruleEngine;
    private final AlertService alertService;
    private final TradeRepository tradeRepository;

    private final Timer processingTimer;
    private final Counter processedCounter;
    private final Counter duplicateCounter;

    public TradeProcessor(RedisStateManager redisStateManager,
                          RuleEngine ruleEngine,
                          AlertService alertService,
                          TradeRepository tradeRepository,
                          MeterRegistry registry) {
        this.redisStateManager = redisStateManager;
        this.ruleEngine        = ruleEngine;
        this.alertService      = alertService;
        this.tradeRepository   = tradeRepository;

        this.processingTimer  = Timer.builder("marketsentry.trade.processing")
                .description("End-to-end processing time for a single trade event")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        this.processedCounter = Counter.builder("marketsentry.trades.processed")
                .description("Trades that passed the dedup gate and were processed")
                .register(registry);
        this.duplicateCounter = Counter.builder("marketsentry.trades.duplicates")
                .description("Trade deliveries skipped by the idempotency gate")
                .register(registry);
    }

    public void process(TradeEvent event) {
        // Idempotency gate: at-least-once delivery means the same trade can arrive
        // more than once (retry, rebalance). Skip if we've already processed it —
        // otherwise the EMA and reversal updates would double-apply.
        if (!redisStateManager.claimTrade(event.getTradeId())) {
            log.debug("Skipping duplicate trade delivery: {}", event.getTradeId());
            duplicateCounter.increment();
            return;
        }

        processingTimer.record(() -> doProcess(event));
        processedCounter.increment();
    }

    private void doProcess(TradeEvent event) {
        persistTrade(event);

        TraderState state = redisStateManager.getOrCreate(event.getTraderId());
        redisStateManager.update(state, event);

        long tradesInWindow    = redisStateManager.getTradesInWindow(event.getTraderId());
        long reversalsInWindow = redisStateManager.getReversalsInWindow(event.getTraderId());

        List<Alert> alerts = ruleEngine.evaluate(event, state, tradesInWindow, reversalsInWindow);

        if (!alerts.isEmpty()) {
            log.info("Generated {} alert(s) for trader: {} | window: {} trades/60s, {} reversals/10s",
                    alerts.size(), event.getTraderId(), tradesInWindow, reversalsInWindow);
            alerts.forEach(alertService::process);
        }
    }

    private void persistTrade(TradeEvent event) {
        Trade trade = Trade.builder()
                .tradeId(event.getTradeId())
                .traderId(event.getTraderId())
                .stock(event.getStock())
                .side(event.getSide().name())
                .quantity(event.getQuantity())
                .price(event.getPrice())
                .timestamp(event.getTimestamp())
                .build();
        tradeRepository.save(trade);
    }
}
