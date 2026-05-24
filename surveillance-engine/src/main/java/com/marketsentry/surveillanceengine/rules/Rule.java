package com.marketsentry.surveillanceengine.rules;

import com.marketsentry.surveillanceengine.model.Alert;

import java.util.Optional;

/**
 * A single surveillance rule. Implementations are auto-discovered as Spring beans
 * and evaluated by RuleEngine for every processed trade.
 */
public interface Rule {

    /**
     * @return an alert if this rule fires for the given context, otherwise empty.
     */
    Optional<Alert> evaluate(RuleContext ctx);
}
