package com.marketsentry.surveillanceengine.api;

import com.marketsentry.surveillanceengine.model.Alert;
import com.marketsentry.surveillanceengine.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertRepository alertRepository;

    @GetMapping
    public Flux<Alert> getAllAlerts() {
        return Flux.fromIterable(alertRepository.findAll());
    }

    @GetMapping("/high-risk")
    public Flux<Alert> getHighRiskAlerts() {
        return Flux.fromIterable(alertRepository.findBySeverity(Alert.Severity.HIGH))
                .concatWith(Flux.fromIterable(alertRepository.findBySeverity(Alert.Severity.CRITICAL)));
    }

    @GetMapping("/{id}")
    public Mono<Alert> getAlert(@PathVariable String id) {
        return Mono.justOrEmpty(alertRepository.findById(id));
    }
}
