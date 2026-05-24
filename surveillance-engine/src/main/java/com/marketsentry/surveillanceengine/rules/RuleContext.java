package com.marketsentry.surveillanceengine.rules;

import com.marketsentry.surveillanceengine.model.TradeEvent;
import com.marketsentry.surveillanceengine.model.TraderState;

/**
 * Immutable bundle of inputs every rule needs to evaluate a single trade.
 * Adding a new input (e.g. an order book snapshot) is a one-line change here
 * — no rule signatures shift.
 */
public record RuleContext(
        TradeEvent trade,
        TraderState state,
        long tradesInWindow,
        long reversalsInWindow
) {}
