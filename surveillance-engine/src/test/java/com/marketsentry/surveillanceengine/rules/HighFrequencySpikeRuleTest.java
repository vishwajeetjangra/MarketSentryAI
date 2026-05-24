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

class HighFrequencySpikeRuleTest {

    private HighFrequencySpikeRule rule;
    private RulesProperties props;

    @BeforeEach
    void setUp() {
        props = new RulesProperties();
        props.getHighFrequency().setThresholdTradesPerMinute(30);
        rule = new HighFrequencySpikeRule(props);
    }

    @Test
    void doesNotFireWhenBelowThreshold() {
        Optional<Alert> result = rule.evaluate(ctx(29));
        assertThat(result).isEmpty();
    }

    @Test
    void doesNotFireAtExactThreshold() {
        // Threshold uses strict `>` — at exactly the threshold the rule is silent.
        Optional<Alert> result = rule.evaluate(ctx(30));
        assertThat(result).isEmpty();
    }

    @Test
    void firesWhenAboveThreshold() {
        Optional<Alert> result = rule.evaluate(ctx(31));
        assertThat(result).isPresent();
        assertThat(result.get().getRuleTriggered()).isEqualTo("HIGH_FREQUENCY_SPIKE");
        assertThat(result.get().getSeverity()).isEqualTo(Alert.Severity.HIGH);
        assertThat(result.get().getMessage()).contains("31");
    }

    private RuleContext ctx(long tradesInWindow) {
        TradeEvent t = RuleTestSupport.trade("trader-1", 100, TradeEvent.TradeSide.BUY);
        TraderState s = RuleTestSupport.state("trader-1", BigDecimal.ZERO);
        return new RuleContext(t, s, tradesInWindow, 0);
    }
}
