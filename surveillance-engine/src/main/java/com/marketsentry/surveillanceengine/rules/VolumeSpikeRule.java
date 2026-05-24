package com.marketsentry.surveillanceengine.rules;

import com.marketsentry.surveillanceengine.config.RulesProperties;
import com.marketsentry.surveillanceengine.model.Alert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class VolumeSpikeRule implements Rule {

    private final RulesProperties props;

    @Override
    public Optional<Alert> evaluate(RuleContext ctx) {
        RulesProperties.VolumeSpike cfg = props.getVolumeSpike();

        // Skip until we have enough trades to form a meaningful baseline.
        if (ctx.tradesInWindow() < cfg.getWarmupTrades()) return Optional.empty();

        BigDecimal avg = ctx.state().getAvgTradeVolume();
        if (avg == null || avg.compareTo(BigDecimal.ZERO) == 0) return Optional.empty();

        BigDecimal currentVolume = new BigDecimal(ctx.trade().getQuantity());
        BigDecimal threshold     = avg.multiply(cfg.getMultiplier());

        if (currentVolume.compareTo(threshold) <= 0) return Optional.empty();

        return Optional.of(Alert.builder()
                .alertId("ALT" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase())
                .traderId(ctx.trade().getTraderId())
                .ruleTriggered("VOLUME_SPIKE")
                .severity(Alert.Severity.MEDIUM)
                .message(String.format("Trade volume %s exceeds %sx EMA baseline of %s",
                        currentVolume, cfg.getMultiplier(), avg))
                .timestamp(LocalDateTime.now())
                .build());
    }
}
