package com.marketsentry.tradegenerator.api;

import com.marketsentry.tradegenerator.service.TradeGeneratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/generator")
@RequiredArgsConstructor
public class GeneratorControlController {

    private final TradeGeneratorService generator;

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
}
