package dev.let.agentic.agent;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Calls the Anthropic Messages API. The API key is only sent as a header, never logged. */
public class AnthropicHttpClient implements ClaudeClient {

    private final RestClient rest;
    private final String model;

    public AnthropicHttpClient(String apiKey, String model) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(90));
        this.rest = RestClient.builder()
                .baseUrl("https://api.anthropic.com")
                .requestFactory(factory)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();
        this.model = model;
    }

    @Override
    public String complete(String system, String user) {
        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", 2000,
                "system", system,
                "messages", List.of(Map.of("role", "user", "content", user)));
        Map<?, ?> response = rest.post()
                .uri("/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        StringBuilder text = new StringBuilder();
        if (response != null && response.get("content") instanceof List<?> blocks) {
            for (Object block : blocks) {
                if (block instanceof Map<?, ?> b && "text".equals(b.get("type")) && b.get("text") != null) {
                    text.append(b.get("text"));
                }
            }
        }
        if (text.isEmpty()) {
            throw new IllegalStateException("Empty response from model");
        }
        return text.toString();
    }
}
