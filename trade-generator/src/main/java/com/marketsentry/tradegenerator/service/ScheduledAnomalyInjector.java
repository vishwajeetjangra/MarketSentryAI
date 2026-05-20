package com.marketsentry.tradegenerator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives AnomalyInjectorService on a schedule. Separate from the injector so
 * REST-triggered injection stays available even when scheduled injection is off.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "marketsentry.generator.anomaly-injection.enabled", havingValue = "true")
public class ScheduledAnomalyInjector {

    private final AnomalyInjectorService injector;

    @Scheduled(fixedRateString = "${marketsentry.generator.anomaly-injection.burst-rate-ms:60000}")
    public void scheduledHighFrequencyBurst() {
        injector.injectHighFrequencyBurst(null);
    }

    @Scheduled(fixedRateString = "${marketsentry.generator.anomaly-injection.volume-rate-ms:90000}")
    public void scheduledVolumeSpike() {
        injector.injectVolumeSpike(null);
    }

    @Scheduled(fixedRateString = "${marketsentry.generator.anomaly-injection.reversal-rate-ms:75000}")
    public void scheduledRapidReversals() {
        injector.injectRapidReversals(null);
    }
}
