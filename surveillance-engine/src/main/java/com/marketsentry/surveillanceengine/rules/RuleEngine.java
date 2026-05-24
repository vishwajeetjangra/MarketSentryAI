package com.marketsentry.surveillanceengine.rules;

import com.marketsentry.surveillanceengine.model.Alert;
import com.marketsentry.surveillanceengine.model.TradeEvent;
import com.marketsentry.surveillanceengine.model.TraderState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Orchestrator over the registered {@link Rule} beans. Spring injects every
 * Rule implementation in the application context, so adding a new rule is
 * a matter of dropping in one new @Component — no edits here required.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleEngine {

    private final List<Rule> rules;

    public List<Alert> evaluate(TradeEvent trade, TraderState state,
                                long tradesInWindow, long reversalsInWindow) {
        RuleContext ctx = new RuleContext(trade, state, tradesInWindow, reversalsInWindow);
        return rules.stream()
                .map(rule -> rule.evaluate(ctx))
                .flatMap(Optional::stream)
                .toList();
    }
}
