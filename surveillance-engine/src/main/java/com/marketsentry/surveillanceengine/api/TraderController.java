package com.marketsentry.surveillanceengine.api;

import com.marketsentry.surveillanceengine.model.Alert;
import com.marketsentry.surveillanceengine.model.Trade;
import com.marketsentry.surveillanceengine.model.TraderState;
import com.marketsentry.surveillanceengine.repository.AlertRepository;
import com.marketsentry.surveillanceengine.repository.TradeRepository;
import com.marketsentry.surveillanceengine.service.RedisStateManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/traders")
@RequiredArgsConstructor
public class TraderController {

    private final AlertRepository alertRepository;
    private final TradeRepository tradeRepository;
    private final RedisStateManager redisStateManager;

    @GetMapping("/{id}/risk")
    public Flux<Alert> getTraderRisk(@PathVariable String id) {
        return Flux.fromIterable(alertRepository.findByTraderId(id));
    }

    @GetMapping("/{id}/activity")
    public Flux<Trade> getTraderActivity(@PathVariable String id) {
        return Flux.fromIterable(tradeRepository.findByTraderId(id));
    }

    @GetMapping("/{id}/state")
    public Mono<Map<String, Object>> getTraderState(@PathVariable String id) {
        TraderState state = redisStateManager.getOrCreate(id);
        long tradesInWindow    = redisStateManager.getTradesInWindow(id);
        long reversalsInWindow = redisStateManager.getReversalsInWindow(id);

        return Mono.just(Map.of(
                "traderId", id,
                "tradesLast60s", tradesInWindow,
                "reversalsLast10s", reversalsInWindow,
                "avgTradeVolume", state.getAvgTradeVolume(),
                "lastTradeSide", state.getLastTradeSide() != null ? state.getLastTradeSide() : "N/A",
                "lastTradeTimestampMs", state.getLastTradeTimestampMs()
        ));
    }
}
