package com.marketsentry.aisummarizer.service;

import com.marketsentry.aisummarizer.model.AiSummary;
import com.marketsentry.aisummarizer.model.Alert;
import com.marketsentry.aisummarizer.repository.SummaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SummaryServiceTest {

    private OllamaClient ollamaClient;
    private SummaryRepository summaryRepository;
    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOps;
    private SummaryService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ollamaClient = mock(OllamaClient.class);
        summaryRepository = mock(SummaryRepository.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        service = new SummaryService(ollamaClient, summaryRepository, stringRedisTemplate);
    }

    @Test
    void firstDelivery_callsOllamaAndSavesSummary() {
        Alert alert = sampleAlert("ALT-1");
        // SETNX succeeds — first time we see this alert.
        when(valueOps.setIfAbsent(startsWith("summary:claim:"), eq("1"), any(Duration.class)))
                .thenReturn(true);
        when(ollamaClient.generate(any())).thenReturn("This is a serious anomaly.");

        service.summarize(alert);

        // Prompt includes the alert body fields the model needs to reason about.
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(ollamaClient).generate(promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("T1001")              // traderId
                .contains("HIGH_FREQUENCY_SPIKE") // ruleTriggered
                .contains("HIGH")                 // severity
                .contains("test message");        // message

        // Summary is persisted with the alert id and Ollama's text.
        ArgumentCaptor<AiSummary> savedCaptor = ArgumentCaptor.forClass(AiSummary.class);
        verify(summaryRepository).save(savedCaptor.capture());
        AiSummary saved = savedCaptor.getValue();
        assertThat(saved.getAlertId()).isEqualTo("ALT-1");
        assertThat(saved.getAiSummary()).isEqualTo("This is a serious anomaly.");
        assertThat(saved.getSummaryId()).isNotBlank();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void duplicateDelivery_skipsOllamaAndRepository() {
        Alert alert = sampleAlert("ALT-2");
        // SETNX returns false — alert id already claimed.
        when(valueOps.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(false);

        service.summarize(alert);

        verifyNoInteractions(ollamaClient);
        verify(summaryRepository, never()).save(any());
    }

    @Test
    void redisReturnsNull_treatedAsNotClaimed() {
        Alert alert = sampleAlert("ALT-3");
        // Defensive: setIfAbsent can return null in some Redis client states.
        when(valueOps.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(null);

        service.summarize(alert);

        verifyNoInteractions(ollamaClient);
        verify(summaryRepository, never()).save(any());
    }

    @Test
    void claimKeyIsScopedToAlertId() {
        Alert alert = sampleAlert("ALT-XYZ");
        when(valueOps.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(true);
        when(ollamaClient.generate(any())).thenReturn("summary");

        service.summarize(alert);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).setIfAbsent(keyCaptor.capture(), eq("1"), any(Duration.class));
        assertThat(keyCaptor.getValue()).isEqualTo("summary:claim:ALT-XYZ");
    }

    @Test
    void ollamaFailure_propagatesAndPreventsSave() {
        Alert alert = sampleAlert("ALT-FAIL");
        when(valueOps.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(true);
        when(ollamaClient.generate(any()))
                .thenThrow(new OllamaClient.OllamaCallException("boom"));

        // The exception is allowed to escape — the Kafka error handler is responsible
        // for retries and DLQ routing, not the service layer.
        org.junit.jupiter.api.Assertions.assertThrows(
                OllamaClient.OllamaCallException.class,
                () -> service.summarize(alert));

        verify(summaryRepository, never()).save(any());
    }

    private Alert sampleAlert(String id) {
        return Alert.builder()
                .alertId(id)
                .traderId("T1001")
                .ruleTriggered("HIGH_FREQUENCY_SPIKE")
                .severity("HIGH")
                .message("test message")
                .timestamp(LocalDateTime.now())
                .build();
    }
}
