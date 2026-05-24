package com.marketsentry.surveillanceengine.rules;

import com.marketsentry.surveillanceengine.config.RulesProperties;
import com.marketsentry.surveillanceengine.model.Alert;
import com.marketsentry.surveillanceengine.model.TradeEvent;
import com.marketsentry.surveillanceengine.model.TraderState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RapidBuySellReversalRuleTest {

    private RapidBuySellReversalRule rule;

    @BeforeEach
    void setUp() {
        RulesProperties props = new RulesProperties();
        props.getRapidReversal().setThresholdReversals(5);
        rule = new RapidBuySellReversalRule(props);
    }

    @Test
    void doesNotFireBelowThreshold() {
        assertThat(rule.evaluate(ctx(4))).isEmpty();
    }

    @Test
    void firesAtThreshold() {
        // Reversal rule uses `>=`, so exactly 5 fires.
        Optional<Alert> result = rule.evaluate(ctx(5));
        assertThat(result).isPresent();
        assertThat(result.get().getRuleTriggered()).isEqualTo("RAPID_BUY_SELL_REVERSAL");
        assertThat(result.get().getSeverity()).isEqualTo(Alert.Severity.HIGH);
        assertThat(result.get().getMessage()).contains("wash trading");
    }

    @Test
    void firesAboveThreshold() {
        assertThat(rule.evaluate(ctx(10))).isPresent();
    }

    private RuleContext ctx(long reversalsInWindow) {
        TradeEvent t = RuleTestSupport.trade("trader-2", 100, TradeEvent.TradeSide.SELL);
        TraderState s = RuleTestSupport.state("trader-2", BigDecimal.ZERO);
        return new RuleContext(t, s, 0, reversalsInWindow);
    }
}
