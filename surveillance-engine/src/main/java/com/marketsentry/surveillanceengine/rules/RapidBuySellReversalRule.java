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
public class RapidBuySellReversalRule implements Rule {

    private final RulesProperties props;

    @Override
    public Optional<Alert> evaluate(RuleContext ctx) {
        long threshold = props.getRapidReversal().getThresholdReversals();
        if (ctx.reversalsInWindow() < threshold) return Optional.empty();

        return Optional.of(Alert.builder()
                .alertId("ALT" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase())
                .traderId(ctx.trade().getTraderId())
                .ruleTriggered("RAPID_BUY_SELL_REVERSAL")
                .severity(Alert.Severity.HIGH)
                .message(String.format(
                        "Trader performed %d rapid buy/sell reversals within 10 seconds — possible wash trading",
                        ctx.reversalsInWindow()))
                .timestamp(LocalDateTime.now())
                .build());
    }
}
