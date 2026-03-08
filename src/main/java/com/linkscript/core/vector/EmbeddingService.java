package com.linkscript.core.vector;

import com.linkscript.infra.ai.AiGateway;
import com.linkscript.infra.ai.VectorProperties;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final AiGateway aiGateway;
    private final VectorProperties vectorProperties;

    public EmbeddingService(AiGateway aiGateway, VectorProperties vectorProperties) {
        this.aiGateway = aiGateway;
        this.vectorProperties = vectorProperties;
    }

    public float[] embed(String text) {
        return aiGateway.embed(text)
                .map(vector -> {
                    float[] normalized = normalizeDimensions(vector);
                    log.info("embedding.resolved source=ai inputChars={} dimensions={}", text.length(), normalized.length);
                    return normalized;
                })
                .orElseGet(() -> {
                    float[] fallback = fallbackEmbedding(text);
                    log.info("embedding.resolved source=fallback inputChars={} dimensions={}", text.length(), fallback.length);
                    return fallback;
                });
    }

    public String toVectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        builder.append(']');
        return builder.toString();
    }

    float[] normalizeDimensions(float[] vector) {
        int dimensions = vectorProperties.dimensions();
        if (vector.length == dimensions) {
            return vector;
        }
        float[] normalized = new float[dimensions];
        System.arraycopy(vector, 0, normalized, 0, Math.min(vector.length, dimensions));
        return normalized;
    }

    float[] fallbackEmbedding(String text) {
        int dimensions = vectorProperties.dimensions();
        float[] vector = new float[dimensions];
        if (!StringUtils.hasText(text)) {
            return vector;
        }

        Set<String> tokens = tokenize(text);
        int i = 1;
        for (String token : tokens) {
            int hash = Math.abs(token.hashCode());
            int index = hash % dimensions;
            vector[index] += 1.0f + (i % 3) * 0.1f;
            i++;
        }

        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        if (norm == 0) {
            return vector;
        }
        for (int index = 0; index < vector.length; index++) {
            vector[index] = (float) (vector[index] / norm);
        }
        return vector;
    }

    private Set<String> tokenize(String text) {
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        Set<String> tokens = new TreeSet<>();
        if (!StringUtils.hasText(normalized)) {
            return tokens;
        }
        for (String token : normalized.split("\\s+")) {
            if (token.length() > 1) {
                tokens.add(token);
            }
        }
        String compact = normalized.replace(" ", "");
        for (int index = 0; index < compact.length() - 1; index++) {
            tokens.add(compact.substring(index, index + 2));
        }
        return tokens;
    }
}
