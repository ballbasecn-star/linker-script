package com.linkscript.infra.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class AiGateway {

    private static final Logger log = LoggerFactory.getLogger(AiGateway.class);

    private final RestClient restClient;
    private final LinkScriptAiProperties properties;

    public AiGateway(RestClient.Builder builder, LinkScriptAiProperties properties) {
        this.properties = properties;
        String baseUrl = StringUtils.hasText(properties.baseUrl()) ? properties.baseUrl() : "https://api.openai.com/v1";

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.resolvedConnectTimeoutSeconds()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.resolvedReadTimeoutSeconds()));

        RestClient.Builder clientBuilder = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory);
        if (StringUtils.hasText(properties.apiKey())) {
            clientBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey());
        }
        this.restClient = clientBuilder.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public Optional<String> chat(String systemPrompt, String userPrompt) {
        if (!isEnabled()) {
            log.info("ai.chat.skipped reason=disabled_or_missing_credentials");
            return Optional.empty();
        }

        String model = defaultIfBlank(properties.chatModel(), "gpt-4.1-mini");
        int maxRetries = properties.resolvedMaxRetries();

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            long startedAt = System.nanoTime();
            try {
                Map<String, Object> payload = Map.of(
                        "model", model,
                        "temperature", properties.temperature() == null ? 0.7 : properties.temperature(),
                        "messages", List.of(
                                Map.of("role", "system", "content", systemPrompt),
                                Map.of("role", "user", "content", userPrompt)));
                log.info("ai.chat.request model={} baseUrl={} attempt={}/{} systemChars={} userChars={}",
                        model,
                        properties.baseUrl(),
                        attempt + 1,
                        maxRetries + 1,
                        systemPrompt.length(),
                        userPrompt.length());

                JsonNode response = restClient.post()
                        .uri("/chat/completions")
                        .body(payload)
                        .retrieve()
                        .body(JsonNode.class);

                Optional<String> content = parseChatContent(response);
                if (content.isPresent()) {
                    log.info("ai.chat.response model={} durationMs={} contentChars={}",
                            model, elapsedMillis(startedAt), content.get().length());
                    return content;
                }
                log.warn("ai.chat.empty_or_missing_content model={} durationMs={}", model, elapsedMillis(startedAt));
                return Optional.empty();
            } catch (Exception exception) {
                log.warn("ai.chat.failed model={} attempt={}/{} durationMs={} message={}",
                        model,
                        attempt + 1,
                        maxRetries + 1,
                        elapsedMillis(startedAt),
                        exception.getMessage());
                if (attempt < maxRetries) {
                    sleepBeforeRetry(attempt);
                }
            }
        }
        return Optional.empty();
    }

    public Optional<float[]> embed(String text) {
        if (!isEnabled() || !StringUtils.hasText(properties.embeddingModel())) {
            log.info("ai.embedding.skipped reason=disabled_or_missing_embedding_model");
            return Optional.empty();
        }

        String model = properties.embeddingModel();
        int maxRetries = properties.resolvedMaxRetries();

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            long startedAt = System.nanoTime();
            try {
                Map<String, Object> payload = Map.of(
                        "model", model,
                        "input", text);
                log.info("ai.embedding.request model={} baseUrl={} attempt={}/{} inputChars={}",
                        model,
                        properties.baseUrl(),
                        attempt + 1,
                        maxRetries + 1,
                        text.length());

                JsonNode response = restClient.post()
                        .uri("/embeddings")
                        .body(payload)
                        .retrieve()
                        .body(JsonNode.class);

                Optional<float[]> vector = parseEmbeddingVector(response);
                if (vector.isPresent()) {
                    log.info("ai.embedding.response model={} durationMs={} dimensions={}",
                            model, elapsedMillis(startedAt), vector.get().length);
                    return vector;
                }
                log.warn("ai.embedding.empty_or_missing_vector model={} durationMs={}", model,
                        elapsedMillis(startedAt));
                return Optional.empty();
            } catch (Exception exception) {
                log.warn("ai.embedding.failed model={} attempt={}/{} durationMs={} message={}",
                        model,
                        attempt + 1,
                        maxRetries + 1,
                        elapsedMillis(startedAt),
                        exception.getMessage());
                if (attempt < maxRetries) {
                    sleepBeforeRetry(attempt);
                }
            }
        }
        return Optional.empty();
    }

    // ---- Parsing helpers ----

    private Optional<String> parseChatContent(JsonNode response) {
        if (response == null) {
            return Optional.empty();
        }
        JsonNode contentNode = response.path("choices").path(0).path("message").path("content");
        if (contentNode.isMissingNode() || contentNode.isNull()) {
            return Optional.empty();
        }
        return Optional.ofNullable(contentNode.asText());
    }

    private Optional<float[]> parseEmbeddingVector(JsonNode response) {
        if (response == null) {
            return Optional.empty();
        }
        JsonNode embeddingNode = response.path("data").path(0).path("embedding");
        if (!embeddingNode.isArray() || embeddingNode.isEmpty()) {
            return Optional.empty();
        }
        List<Float> values = new ArrayList<>(embeddingNode.size());
        for (JsonNode node : embeddingNode) {
            values.add(node.floatValue());
        }
        float[] vector = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            vector[i] = values.get(i);
        }
        return Optional.of(vector);
    }

    // ---- Utility methods ----

    private void sleepBeforeRetry(int attempt) {
        long delayMs = (long) Math.pow(2, attempt) * 1000;
        log.info("ai.retry.backoff attempt={} delayMs={}", attempt + 1, delayMs);
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    boolean isEnabled() {
        return properties.enabled() && StringUtils.hasText(properties.apiKey())
                && StringUtils.hasText(properties.baseUrl());
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
