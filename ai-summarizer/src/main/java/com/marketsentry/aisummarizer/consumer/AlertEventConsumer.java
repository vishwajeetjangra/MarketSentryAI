package com.marketsentry.aisummarizer.consumer;

import com.marketsentry.aisummarizer.model.Alert;
import com.marketsentry.aisummarizer.service.SummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEventConsumer {

    private final SummaryService summaryService;

    @KafkaListener(
            topics = "${marketsentry.kafka.topics.surveillance-alerts}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(Alert alert) {
        log.info("Received alert for summarization: {} | trader: {}", alert.getAlertId(), alert.getTraderId());
        summaryService.summarize(alert);
    }
}
