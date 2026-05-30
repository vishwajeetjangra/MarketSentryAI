package com.marketsentry.aisummarizer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * Thin wrapper around the Ollama HTTP API.
 *
 * Failure semantics: any network/timeout/HTTP/empty-response error is wrapped
 * in {@link OllamaCallException} (a RuntimeException) and rethrown. That lets
 * the Kafka consumer's configured retry + DLQ machinery do its job — earlier
 * versions of this class swallowed everything and returned a fallback string,
 * which silently disabled the retry handler.
 */
@Slf4j
@Component
public class OllamaClient {

    private final RestTemplate restTemplate;
    private final String ollamaUrl;
    private final String model;

    public OllamaClient(
            RestTemplateBuilder builder,
            @Value("${marketsentry.ollama.url}") String ollamaUrl,
            @Value("${marketsentry.ollama.model}") String model,
            @Value("${marketsentry.ollama.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${marketsentry.ollama.read-timeout-ms:60000}") long readTimeoutMs) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
        this.ollamaUrl = ollamaUrl;
        this.model = model;
    }

    public String generate(String prompt) {
        Map<String, Object> request = Map.of(
                "model", model,
                "prompt", prompt,
                "stream", false
        );
        Map<?, ?> response;
        try {
            response = restTemplate.postForObject(ollamaUrl + "/api/generate", request, Map.class);
        } catch (RestClientException e) {
            throw new OllamaCallException("Ollama request failed", e);
        }
        if (response == null || !(response.get("response") instanceof String text) || text.isBlank()) {
            throw new OllamaCallException("Ollama returned an empty or malformed response for model " + model);
        }
        return text;
    }

    /** Wrapper exception so consumers can classify and route Ollama failures. */
    public static class OllamaCallException extends RuntimeException {
        public OllamaCallException(String message) { super(message); }
        public OllamaCallException(String message, Throwable cause) { super(message, cause); }
    }
}
