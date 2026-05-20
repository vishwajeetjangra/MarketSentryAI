package com.marketsentry.tradegenerator.api;

import com.marketsentry.tradegenerator.service.AnomalyInjectorService;
import com.marketsentry.tradegenerator.service.TradeGeneratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/generator")
@RequiredArgsConstructor
public class GeneratorControlController {

    private final TradeGeneratorService generator;
    private final AnomalyInjectorService anomalyInjector;

    @GetMapping("/status")
    public Map<String, Object> status() {
        return generator.status();
    }

    @PostMapping("/start")
    public Map<String, Object> start() {
        boolean changed = generator.start();
        return Map.of("action", "start", "changed", changed, "status", generator.status());
    }

    @PostMapping("/stop")
    public Map<String, Object> stop() {
        boolean changed = generator.stop();
        return Map.of("action", "stop", "changed", changed, "status", generator.status());
    }

    @PostMapping("/rate")
    public Map<String, Object> setRate(@RequestParam long ms) {
        generator.setRate(ms);
        return Map.of("action", "setRate", "status", generator.status());
    }

    /**
     * On-demand anomaly injection.
     *   POST /generator/inject/HIGH_FREQUENCY
     *   POST /generator/inject/VOLUME_SPIKE?trader=T9002
     *   POST /generator/inject/REVERSAL
     *
     * Invalid rule names get a 400 automatically from Spring's enum conversion.
     */
    @PostMapping("/inject/{rule}")
    public Map<String, Object> inject(
            @PathVariable AnomalyRule rule,
            @RequestParam(required = false) String trader) {

        String usedTrader = switch (rule) {
            case HIGH_FREQUENCY -> anomalyInjector.injectHighFrequencyBurst(trader);
            case VOLUME_SPIKE   -> anomalyInjector.injectVolumeSpike(trader);
            case REVERSAL       -> anomalyInjector.injectRapidReversals(trader);
        };

        return Map.of(
                "rule", rule.name(),
                "traderId", usedTrader,
                "requestedTrader", trader != null ? trader : "random"
        );
    }

    public enum AnomalyRule {
        HIGH_FREQUENCY,
        VOLUME_SPIKE,
        REVERSAL
    }
}
