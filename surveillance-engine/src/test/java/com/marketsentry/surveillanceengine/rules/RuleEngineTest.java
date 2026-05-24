package com.marketsentry.surveillanceengine.rules;

import com.marketsentry.surveillanceengine.model.Alert;
import com.marketsentry.surveillanceengine.model.TradeEvent;
import com.marketsentry.surveillanceengine.model.TraderState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEngineTest {

    private static final Alert SAMPLE_ALERT = Alert.builder()
            .alertId("A1")
            .traderId("t1")
            .ruleTriggered("TEST")
            .severity(Alert.Severity.LOW)
            .message("m")
            .timestamp(LocalDateTime.now())
            .build();

    @Test
    void returnsEmptyWhenNoRulesFire() {
        RuleEngine engine = new RuleEngine(List.of(ctx -> Optional.empty(), ctx -> Optional.empty()));
        assertThat(engine.evaluate(trade(), state(), 0, 0)).isEmpty();
    }

    @Test
    void aggregatesAlertsFromMultipleRules() {
        Rule fires = ctx -> Optional.of(SAMPLE_ALERT);
        Rule silent = ctx -> Optional.empty();

        RuleEngine engine = new RuleEngine(List.of(fires, silent, fires));
        List<Alert> alerts = engine.evaluate(trade(), state(), 0, 0);

        assertThat(alerts).hasSize(2);
    }

    @Test
    void handlesEmptyRuleList() {
        RuleEngine engine = new RuleEngine(List.of());
        assertThat(engine.evaluate(trade(), state(), 0, 0)).isEmpty();
    }

    private TradeEvent trade() {
        return RuleTestSupport.trade("t1", 100, TradeEvent.TradeSide.BUY);
    }

    private TraderState state() {
        return RuleTestSupport.state("t1", BigDecimal.ZERO);
    }
}
