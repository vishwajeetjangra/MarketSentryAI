package com.marketsentry.aisummarizer.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OllamaClientTest {

    private static final String URL = "http://localhost:11434";
    private static final String MODEL = "phi3:mini";

    private OllamaClient client;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        client = new OllamaClient(new RestTemplateBuilder(), URL, MODEL, 1000L, 5000L);
        // Bind a MockRestServiceServer to the RestTemplate the client built internally,
        // so we can stub the HTTP responses without doing real network calls.
        RestTemplate template = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        mockServer = MockRestServiceServer.createServer(template);
    }

    @Test
    void returnsResponseTextOnHappyPath() {
        mockServer.expect(requestTo(URL + "/api/generate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"response\":\"Suspicious trader activity detected.\",\"done\":true}",
                        MediaType.APPLICATION_JSON));

        String result = client.generate("Summarize this alert");

        assertThat(result).isEqualTo("Suspicious trader activity detected.");
        mockServer.verify();
    }

    @Test
    void throwsWhenResponseFieldIsMissing() {
        mockServer.expect(requestTo(URL + "/api/generate"))
                .andRespond(withSuccess("{\"done\":true}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generate("anything"))
                .isInstanceOf(OllamaClient.OllamaCallException.class)
                .hasMessageContaining("empty or malformed");
    }

    @Test
    void throwsWhenResponseFieldIsBlank() {
        mockServer.expect(requestTo(URL + "/api/generate"))
                .andRespond(withSuccess("{\"response\":\"   \"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generate("anything"))
                .isInstanceOf(OllamaClient.OllamaCallException.class);
    }

    @Test
    void throwsWhenResponseFieldIsWrongType() {
        // "response": 42 — integer where a string was expected.
        mockServer.expect(requestTo(URL + "/api/generate"))
                .andRespond(withSuccess("{\"response\":42}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generate("anything"))
                .isInstanceOf(OllamaClient.OllamaCallException.class);
    }

    @Test
    void wrapsHttpErrorAsOllamaCallException() {
        mockServer.expect(requestTo(URL + "/api/generate"))
                .andRespond(withServerError().body("Internal error"));

        assertThatThrownBy(() -> client.generate("anything"))
                .isInstanceOf(OllamaClient.OllamaCallException.class)
                .hasMessageContaining("Ollama request failed")
                .hasCauseInstanceOf(RestClientException.class);
    }
}
