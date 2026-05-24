package com.marketsentry.surveillanceengine.service;

import com.marketsentry.surveillanceengine.model.Alert;
import com.marketsentry.surveillanceengine.repository.AlertRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
public class AlertService {

    private static final String COOLDOWN_KEY_PREFIX = "alert:cooldown:";

    private final AlertRepository alertRepository;
    private final KafkaTemplate<String, Alert> alertKafkaTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final MeterRegistry registry;

    @Value("${marketsentry.kafka.topics.surveillance-alerts}")
    private String alertsTopic;

    @Value("${marketsentry.surveillance.alert-cooldown-seconds:60}")
    private long cooldownSeconds;

    public AlertService(AlertRepository alertRepository,
                        KafkaTemplate<String, Alert> alertKafkaTemplate,
                        StringRedisTemplate stringRedisTemplate,
                        MeterRegistry registry) {
        this.alertRepository    = alertRepository;
        this.alertKafkaTemplate = alertKafkaTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.registry           = registry;
    }

    public void process(Alert alert) {
        if (!claimCooldown(alert)) {
            log.debug("Suppressed duplicate alert | trader: {} | rule: {} (cooldown active)",
                    alert.getTraderId(), alert.getRuleTriggered());
            registry.counter("marketsentry.alerts.suppressed", "rule", alert.getRuleTriggered()).increment();
            return;
        }
        alertRepository.save(alert);
        alertKafkaTemplate.send(alertsTopic, alert.getTraderId(), alert);
        registry.counter("marketsentry.alerts.fired",
                "rule", alert.getRuleTriggered(),
                "severity", alert.getSeverity().name()).increment();
        log.info("Alert processed: {} | trader: {} | rule: {} | severity: {}",
                alert.getAlertId(), alert.getTraderId(), alert.getRuleTriggered(), alert.getSeverity());
    }

    /**
     * Atomically claims a per-(trader, rule) cooldown slot in Redis.
     * Returns true if the slot was free (alert should fire) and false if a
     * cooldown is already active (alert should be suppressed).
     */
    private boolean claimCooldown(Alert alert) {
        String key = COOLDOWN_KEY_PREFIX + alert.getTraderId() + ":" + alert.getRuleTriggered();
        Boolean claimed = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, alert.getAlertId(), Duration.ofSeconds(cooldownSeconds));
        return Boolean.TRUE.equals(claimed);
    }
}
