package com.linkscript.infra.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
        RestClient.Builder clientBuilder = builder.baseUrl(baseUrl);
        if (StringUtils.hasText(properties.apiKey())) {
            clientBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey());
        }
        this.restClient = clientBuilder.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).build();
    }

    public Optional<String> chat(String systemPrompt, String userPrompt) {
        if (!isEnabled()) {
            log.info("ai.chat.skipped reason=disabled_or_missing_credentials");
            return Optional.empty();
        }

        long startedAt = System.nanoTime();
        String model = defaultIfBlank(properties.chatModel(), "gpt-4.1-mini");
        try {
            Map<String, Object> payload = Map.of(
                    "model", model,
                    "temperature", properties.temperature() == null ? 0.7 : properties.temperature(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    )
            );
            log.info("ai.chat.request model={} baseUrl={} systemChars={} userChars={}",
                    model,
                    properties.baseUrl(),
                    systemPrompt.length(),
                    userPrompt.length()
            );

            JsonNode response = restClient.post()
                    .uri("/chat/completions")
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                log.warn("ai.chat.empty_response model={} durationMs={}", model, elapsedMillis(startedAt));
                return Optional.empty();
            }

            JsonNode contentNode = response.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.isNull()) {
                log.warn("ai.chat.missing_content model={} durationMs={}", model, elapsedMillis(startedAt));
                return Optional.empty();
            }
            String content = contentNode.asText();
            log.info("ai.chat.response model={} durationMs={} contentChars={}",
                    model,
                    elapsedMillis(startedAt),
                    content.length()
            );
            return Optional.ofNullable(content);
        } catch (Exception exception) {
            log.warn("ai.chat.failed model={} durationMs={} message={}",
                    model,
                    elapsedMillis(startedAt),
                    exception.getMessage()
            );
            return Optional.empty();
        }
    }

    public Optional<float[]> embed(String text) {
        if (!isEnabled() || !StringUtils.hasText(properties.embeddingModel())) {
            log.info("ai.embedding.skipped reason=disabled_or_missing_embedding_model");
            return Optional.empty();
        }

        long startedAt = System.nanoTime();
        String model = properties.embeddingModel();
        try {
            Map<String, Object> payload = Map.of(
                    "model", model,
                    "input", text
            );
            log.info("ai.embedding.request model={} baseUrl={} inputChars={}",
                    model,
                    properties.baseUrl(),
                    text.length()
            );

            JsonNode response = restClient.post()
                    .uri("/embeddings")
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                log.warn("ai.embedding.empty_response model={} durationMs={}", model, elapsedMillis(startedAt));
                return Optional.empty();
            }

            JsonNode embeddingNode = response.path("data").path(0).path("embedding");
            if (!embeddingNode.isArray() || embeddingNode.isEmpty()) {
                log.warn("ai.embedding.missing_vector model={} durationMs={}", model, elapsedMillis(startedAt));
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
            log.info("ai.embedding.response model={} durationMs={} dimensions={}",
                    model,
                    elapsedMillis(startedAt),
                    vector.length
            );
            return Optional.of(vector);
        } catch (Exception exception) {
            log.warn("ai.embedding.failed model={} durationMs={} message={}",
                    model,
                    elapsedMillis(startedAt),
                    exception.getMessage()
            );
            return Optional.empty();
        }
    }

    private boolean isEnabled() {
        return properties.enabled() && StringUtils.hasText(properties.apiKey()) && StringUtils.hasText(properties.baseUrl());
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
