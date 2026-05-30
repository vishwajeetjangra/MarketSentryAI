package com.marketsentry.aisummarizer.service;

import com.marketsentry.aisummarizer.model.Alert;
import com.marketsentry.aisummarizer.model.AiSummary;
import com.marketsentry.aisummarizer.repository.SummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryService {

    private static final String CLAIM_KEY_PREFIX = "summary:claim:";
    private static final Duration CLAIM_TTL = Duration.ofHours(1);

    private final OllamaClient ollamaClient;
    private final SummaryRepository summaryRepository;
    private final StringRedisTemplate stringRedisTemplate;

    public void summarize(Alert alert) {
        // Dedup gate: if this alert id has already been (or is being) summarized,
        // skip so a Kafka redelivery doesn't generate a second AiSummary row and,
        // more importantly, doesn't burn another Ollama inference for nothing.
        if (!claimAlert(alert.getAlertId())) {
            log.debug("Skipping duplicate alert delivery: {}", alert.getAlertId());
            return;
        }

        String prompt = buildPrompt(alert);
        String summary = ollamaClient.generate(prompt);

        AiSummary aiSummary = AiSummary.builder()
                .summaryId(UUID.randomUUID().toString())
                .alertId(alert.getAlertId())
                .aiSummary(summary)
                .createdAt(LocalDateTime.now())
                .build();

        summaryRepository.save(aiSummary);
        log.info("Summary saved for alert: {}", alert.getAlertId());
    }

    /**
     * Atomically claims an alert id via SET ... NX EX. Returns true the first time
     * we see a given alertId, false on any redelivery within the TTL.
     */
    private boolean claimAlert(String alertId) {
        Boolean claimed = stringRedisTemplate.opsForValue()
                .setIfAbsent(CLAIM_KEY_PREFIX + alertId, "1", CLAIM_TTL);
        return Boolean.TRUE.equals(claimed);
    }

    private String buildPrompt(Alert alert) {
        return String.format("""
                You are a financial compliance analyst. A surveillance system generated the following alert.
                Provide a concise 2-3 sentence explanation of the suspicious activity and its potential market impact.

                Trader ID: %s
                Rule Triggered: %s
                Severity: %s
                Details: %s

                Summary:""",
                alert.getTraderId(),
                alert.getRuleTriggered(),
                alert.getSeverity(),
                alert.getMessage());
    }
}
