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

class VolumeSpikeRuleTest {

    private VolumeSpikeRule rule;
    private RulesProperties props;

    @BeforeEach
    void setUp() {
        props = new RulesProperties();
        props.getVolumeSpike().setMultiplier(new BigDecimal("5"));
        props.getVolumeSpike().setWarmupTrades(10);
        rule = new VolumeSpikeRule(props);
    }

    @Test
    void doesNotFireDuringWarmup() {
        TraderState state = RuleTestSupport.state("t1", new BigDecimal("100"));
        TradeEvent trade  = RuleTestSupport.trade("t1", 10_000, TradeEvent.TradeSide.BUY);
        RuleContext ctx   = new RuleContext(trade, state, 5, 0); // window < warmup

        assertThat(rule.evaluate(ctx)).isEmpty();
    }

    @Test
    void doesNotFireWhenAvgIsZero() {
        TraderState state = RuleTestSupport.state("t1", BigDecimal.ZERO);
        TradeEvent trade  = RuleTestSupport.trade("t1", 10_000, TradeEvent.TradeSide.BUY);
        RuleContext ctx   = new RuleContext(trade, state, 20, 0);

        assertThat(rule.evaluate(ctx)).isEmpty();
    }

    @Test
    void doesNotFireWhenVolumeAtThreshold() {
        // 5 * 100 = 500; quantity 500 is NOT greater than threshold (strict `>`).
        TraderState state = RuleTestSupport.state("t1", new BigDecimal("100"));
        TradeEvent trade  = RuleTestSupport.trade("t1", 500, TradeEvent.TradeSide.BUY);
        RuleContext ctx   = new RuleContext(trade, state, 20, 0);

        assertThat(rule.evaluate(ctx)).isEmpty();
    }

    @Test
    void firesWhenVolumeExceedsThreshold() {
        TraderState state = RuleTestSupport.state("t1", new BigDecimal("100"));
        TradeEvent trade  = RuleTestSupport.trade("t1", 501, TradeEvent.TradeSide.BUY);
        RuleContext ctx   = new RuleContext(trade, state, 20, 0);

        Optional<Alert> result = rule.evaluate(ctx);
        assertThat(result).isPresent();
        assertThat(result.get().getRuleTriggered()).isEqualTo("VOLUME_SPIKE");
        assertThat(result.get().getSeverity()).isEqualTo(Alert.Severity.MEDIUM);
    }
}
