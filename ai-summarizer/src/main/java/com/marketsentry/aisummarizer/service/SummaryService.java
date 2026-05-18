package com.marketsentry.aisummarizer.service;

import com.marketsentry.aisummarizer.model.Alert;
import com.marketsentry.aisummarizer.model.AiSummary;
import com.marketsentry.aisummarizer.repository.SummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryService {

    private final OllamaClient ollamaClient;
    private final SummaryRepository summaryRepository;

    public void summarize(Alert alert) {
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
