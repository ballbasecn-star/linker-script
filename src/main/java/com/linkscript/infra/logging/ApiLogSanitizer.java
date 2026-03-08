package com.linkscript.infra.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ApiLogSanitizer {

    private static final String REDACTED = "***";

    private final ObjectMapper objectMapper;
    private final LinkScriptLoggingProperties properties;

    public ApiLogSanitizer(ObjectMapper objectMapper, LinkScriptLoggingProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public String sanitize(Object value) {
        if (value == null) {
            return "null";
        }

        try {
            JsonNode tree = objectMapper.valueToTree(value);
            sanitizeNode(tree);
            String serialized = objectMapper.writeValueAsString(tree);
            return truncate(serialized);
        } catch (IllegalArgumentException exception) {
            return truncate(String.valueOf(value));
        } catch (Exception exception) {
            return truncate(value.getClass().getSimpleName());
        }
    }

    private void sanitizeNode(JsonNode node) {
        if (node == null) {
            return;
        }

        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isSensitive(field.getKey())) {
                    objectNode.put(field.getKey(), REDACTED);
                    continue;
                }
                sanitizeNode(field.getValue());
            }
            return;
        }

        if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (JsonNode item : arrayNode) {
                sanitizeNode(item);
            }
        }
    }

    private boolean isSensitive(String fieldName) {
        String normalized = fieldName.toLowerCase(Locale.ROOT);
        return normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("apiKey".toLowerCase(Locale.ROOT))
                || normalized.contains("authorization");
    }

    private String truncate(String value) {
        int maxLength = Math.max(120, properties.maxPayloadLength());
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...(truncated)";
    }
}
