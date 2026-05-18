package com.marketsentry.surveillanceengine.processor;

import com.marketsentry.surveillanceengine.model.Alert;
import com.marketsentry.surveillanceengine.model.Trade;
import com.marketsentry.surveillanceengine.model.TradeEvent;
import com.marketsentry.surveillanceengine.model.TraderState;
import com.marketsentry.surveillanceengine.repository.TradeRepository;
import com.marketsentry.surveillanceengine.rules.RuleEngine;
import com.marketsentry.surveillanceengine.service.AlertService;
import com.marketsentry.surveillanceengine.service.RedisStateManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeProcessor {

    private final RedisStateManager redisStateManager;
    private final RuleEngine ruleEngine;
    private final AlertService alertService;
    private final TradeRepository tradeRepository;

    public void process(TradeEvent event) {
        persistTrade(event);

        TraderState state = redisStateManager.getOrCreate(event.getTraderId());
        redisStateManager.update(state, event);

        // True sliding window count — from the Redis ZSET, not an internal counter
        long tradesInWindow = redisStateManager.getTradesInWindow(event.getTraderId());

        List<Alert> alerts = ruleEngine.evaluate(event, state, tradesInWindow);

        if (!alerts.isEmpty()) {
            log.info("Generated {} alert(s) for trader: {} | window: {} trades/60s",
                    alerts.size(), event.getTraderId(), tradesInWindow);
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
