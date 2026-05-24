package com.marketsentry.surveillanceengine.rules;

import com.marketsentry.surveillanceengine.model.TradeEvent;
import com.marketsentry.surveillanceengine.model.TraderState;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Test data builders shared across rule unit tests. */
final class RuleTestSupport {

    private RuleTestSupport() {}

    static TradeEvent trade(String traderId, long quantity, TradeEvent.TradeSide side) {
        return TradeEvent.builder()
                .tradeId("T-" + System.nanoTime())
                .traderId(traderId)
                .stock("AAPL")
                .side(side)
                .quantity(quantity)
                .price(new BigDecimal("100.00"))
                .timestamp(LocalDateTime.now())
                .build();
    }

    static TraderState state(String traderId, BigDecimal avgVolume) {
        return TraderState.builder()
                .traderId(traderId)
                .avgTradeVolume(avgVolume)
                .build();
    }
}
