package com.marketsentry.surveillanceengine.api;

import com.marketsentry.surveillanceengine.model.Alert;
import com.marketsentry.surveillanceengine.repository.AlertRepository;
import com.marketsentry.surveillanceengine.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {

    private final AlertRepository alertRepository;
    private final TradeRepository tradeRepository;

    @GetMapping
    public Mono<Map<String, Object>> getStats() {
        long totalTrades = tradeRepository.count();
        long totalAlerts = alertRepository.count();
        long highAlerts = alertRepository.findBySeverity(Alert.Severity.HIGH).size();
        long criticalAlerts = alertRepository.findBySeverity(Alert.Severity.CRITICAL).size();

        return Mono.just(Map.of(
                "totalTrades", totalTrades,
                "totalAlerts", totalAlerts,
                "highSeverityAlerts", highAlerts,
                "criticalSeverityAlerts", criticalAlerts
        ));
    }

    @GetMapping("/alerts")
    public Mono<Map<String, Object>> getAlertStats() {
        return Mono.just(Map.of(
                "LOW", alertRepository.findBySeverity(Alert.Severity.LOW).size(),
                "MEDIUM", alertRepository.findBySeverity(Alert.Severity.MEDIUM).size(),
                "HIGH", alertRepository.findBySeverity(Alert.Severity.HIGH).size(),
                "CRITICAL", alertRepository.findBySeverity(Alert.Severity.CRITICAL).size()
        ));
    }
}
