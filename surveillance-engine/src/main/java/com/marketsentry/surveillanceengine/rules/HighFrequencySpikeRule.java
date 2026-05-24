package com.marketsentry.surveillanceengine.rules;

import com.marketsentry.surveillanceengine.config.RulesProperties;
import com.marketsentry.surveillanceengine.model.Alert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class HighFrequencySpikeRule implements Rule {

    private final RulesProperties props;

    @Override
    public Optional<Alert> evaluate(RuleContext ctx) {
        long threshold = props.getHighFrequency().getThresholdTradesPerMinute();
        if (ctx.tradesInWindow() <= threshold) return Optional.empty();

        return Optional.of(Alert.builder()
                .alertId("ALT" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase())
                .traderId(ctx.trade().getTraderId())
                .ruleTriggered("HIGH_FREQUENCY_SPIKE")
                .severity(Alert.Severity.HIGH)
                .message(String.format("Trader executed %d trades in the last 60 seconds", ctx.tradesInWindow()))
                .timestamp(LocalDateTime.now())
                .build());
    }
}
