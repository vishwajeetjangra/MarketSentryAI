package com.marketsentry.surveillanceengine.service;

import com.marketsentry.surveillanceengine.model.Alert;
import com.marketsentry.surveillanceengine.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final KafkaTemplate<String, Alert> alertKafkaTemplate;

    @Value("${marketsentry.kafka.topics.surveillance-alerts}")
    private String alertsTopic;

    public void process(Alert alert) {
        alertRepository.save(alert);
        alertKafkaTemplate.send(alertsTopic, alert.getTraderId(), alert);
        log.info("Alert processed: {} | trader: {} | rule: {} | severity: {}",
                alert.getAlertId(), alert.getTraderId(), alert.getRuleTriggered(), alert.getSeverity());
    }
}
