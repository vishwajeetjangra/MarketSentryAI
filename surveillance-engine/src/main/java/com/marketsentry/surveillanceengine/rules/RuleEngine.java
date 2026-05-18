package com.marketsentry.surveillanceengine.rules;

import com.marketsentry.surveillanceengine.model.Alert;
import com.marketsentry.surveillanceengine.model.TradeEvent;
import com.marketsentry.surveillanceengine.model.TraderState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class RuleEngine {

    private static final long HIGH_FREQUENCY_THRESHOLD   = 30;
    private static final BigDecimal VOLUME_SPIKE_MULTIPLIER = new BigDecimal("5");
    private static final long RAPID_REVERSAL_THRESHOLD   = 5;
    // Warmup: skip volume spike check until we have enough baseline trades
    private static final long VOLUME_WARMUP_TRADES       = 10;

    /**
     * @param tradesInWindow true count of trades in the last 60s from the Redis ZSET sliding window
     */
    public List<Alert> evaluate(TradeEvent trade, TraderState state, long tradesInWindow) {
        List<Alert> alerts = new ArrayList<>();

        checkHighFrequencySpike(trade, tradesInWindow, alerts);
        checkVolumeSpike(trade, state, tradesInWindow, alerts);
        checkRapidBuySellReversal(trade, state, alerts);

        return alerts;
    }

    private void checkHighFrequencySpike(TradeEvent trade, long tradesInWindow, List<Alert> alerts) {
        if (tradesInWindow > HIGH_FREQUENCY_THRESHOLD) {
            alerts.add(buildAlert(
                    trade.getTraderId(),
                    "HIGH_FREQUENCY_SPIKE",
                    Alert.Severity.HIGH,
                    String.format("Trader executed %d trades in the last 60 seconds", tradesInWindow)
            ));
        }
    }

    private void checkVolumeSpike(TradeEvent trade, TraderState state, long tradesInWindow, List<Alert> alerts) {
        // Skip until baseline is established to avoid false positives on first few trades
        if (tradesInWindow < VOLUME_WARMUP_TRADES) return;

        BigDecimal avg = state.getAvgTradeVolume();
        if (avg == null || avg.compareTo(BigDecimal.ZERO) == 0) return;

        BigDecimal currentVolume = new BigDecimal(trade.getQuantity());
        BigDecimal threshold = avg.multiply(VOLUME_SPIKE_MULTIPLIER);

        if (currentVolume.compareTo(threshold) > 0) {
            alerts.add(buildAlert(
                    trade.getTraderId(),
                    "VOLUME_SPIKE",
                    Alert.Severity.MEDIUM,
                    String.format("Trade volume %s exceeds 5x EMA baseline of %s", currentVolume, avg)
            ));
        }
    }

    private void checkRapidBuySellReversal(TradeEvent trade, TraderState state, List<Alert> alerts) {
        if (state.getRapidReversalCount() >= RAPID_REVERSAL_THRESHOLD) {
            alerts.add(buildAlert(
                    trade.getTraderId(),
                    "RAPID_BUY_SELL_REVERSAL",
                    Alert.Severity.HIGH,
                    String.format("Trader performed %d rapid buy/sell reversals within 10 seconds — possible wash trading",
                            state.getRapidReversalCount())
            ));
        }
    }

    private Alert buildAlert(String traderId, String rule, Alert.Severity severity, String message) {
        return Alert.builder()
                .alertId("ALT" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase())
                .traderId(traderId)
                .ruleTriggered(rule)
                .severity(severity)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
