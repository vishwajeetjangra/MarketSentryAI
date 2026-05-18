package com.marketsentry.aisummarizer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

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
        try {
            Map<String, Object> request = Map.of(
                    "model", model,
                    "prompt", prompt,
                    "stream", false
            );
            Map<?, ?> response = restTemplate.postForObject(
                    ollamaUrl + "/api/generate", request, Map.class);
            if (response != null && response.containsKey("response")) {
                return (String) response.get("response");
            }
            log.warn("Ollama returned no 'response' field for model {}", model);
        } catch (Exception e) {
            log.error("Ollama request failed ({}): {}", e.getClass().getSimpleName(), e.getMessage());
        }
        return "AI summary unavailable.";
    }
}
