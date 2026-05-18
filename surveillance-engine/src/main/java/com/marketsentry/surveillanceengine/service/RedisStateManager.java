package com.marketsentry.surveillanceengine.service;

import com.marketsentry.surveillanceengine.model.TradeEvent;
import com.marketsentry.surveillanceengine.model.TraderState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisStateManager {

    private final RedisTemplate<String, TraderState> traderStateRedisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String STATE_KEY_PREFIX  = "trader:state:";
    private static final String WINDOW_KEY_PREFIX = "trader:window:";

    private static final long     WINDOW_MS        = 60_000;
    private static final long     REVERSAL_WINDOW_MS = 10_000;
    private static final Duration STATE_TTL        = Duration.ofMinutes(10);
    private static final Duration WINDOW_TTL       = Duration.ofMinutes(2);

    public TraderState getOrCreate(String traderId) {
        TraderState state = traderStateRedisTemplate.opsForValue().get(STATE_KEY_PREFIX + traderId);
        if (state == null) {
            state = TraderState.builder()
                    .traderId(traderId)
                    .avgTradeVolume(BigDecimal.ZERO)
                    .rapidReversalCount(0)
                    .build();
        }
        return state;
    }

    /**
     * Returns the number of trades this trader made in the last 60 seconds
     * by querying the Redis sorted set window, pruning stale entries first.
     */
    public long getTradesInWindow(String traderId) {
        String key = WINDOW_KEY_PREFIX + traderId;
        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_MS;

        stringRedisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart - 1);
        Long count = stringRedisTemplate.opsForZSet().count(key, windowStart, now);
        return count != null ? count : 0;
    }

    public void update(TraderState state, TradeEvent trade) {
        long now = System.currentTimeMillis();
        String windowKey = WINDOW_KEY_PREFIX + trade.getTraderId();

        // Add this trade to the sliding window; score = timestamp so range queries work
        stringRedisTemplate.opsForZSet().add(windowKey, trade.getTradeId(), now);
        // Prune entries that have fallen outside the 60s window
        stringRedisTemplate.opsForZSet().removeRangeByScore(windowKey, 0, now - WINDOW_MS);
        stringRedisTemplate.expire(windowKey, WINDOW_TTL);

        // Update EMA volume baseline
        BigDecimal currentVol = new BigDecimal(trade.getQuantity());
        if (state.getAvgTradeVolume().compareTo(BigDecimal.ZERO) == 0) {
            state.setAvgTradeVolume(currentVol);
        } else {
            BigDecimal updated = state.getAvgTradeVolume()
                    .multiply(new BigDecimal("0.9"))
                    .add(currentVol.multiply(new BigDecimal("0.1")))
                    .setScale(2, RoundingMode.HALF_UP);
            state.setAvgTradeVolume(updated);
        }

        // Update rapid reversal tracking
        String newSide = trade.getSide().name();
        if (state.getLastTradeSide() != null
                && !state.getLastTradeSide().equals(newSide)
                && (now - state.getLastTradeTimestampMs()) < REVERSAL_WINDOW_MS) {
            state.setRapidReversalCount(state.getRapidReversalCount() + 1);
        } else if (state.getLastTradeSide() != null && state.getLastTradeSide().equals(newSide)) {
            state.setRapidReversalCount(0);
        }

        state.setLastTradeSide(newSide);
        state.setLastTradeTimestampMs(now);

        traderStateRedisTemplate.opsForValue().set(STATE_KEY_PREFIX + trade.getTraderId(), state, STATE_TTL);
    }
}
