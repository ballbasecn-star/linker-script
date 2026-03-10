package com.linkscript.infra.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "linkscript.ai")
public record LinkScriptAiProperties(
                boolean enabled,
                String apiKey,
                String baseUrl,
                String chatModel,
                String embeddingModel,
                Double temperature,
                Integer connectTimeoutSeconds,
                Integer readTimeoutSeconds,
                Integer maxRetries) {
        public int resolvedConnectTimeoutSeconds() {
                return connectTimeoutSeconds != null ? connectTimeoutSeconds : 10;
        }

        public int resolvedReadTimeoutSeconds() {
                return readTimeoutSeconds != null ? readTimeoutSeconds : 60;
        }

        public int resolvedMaxRetries() {
                return maxRetries != null ? maxRetries : 2;
        }
}
