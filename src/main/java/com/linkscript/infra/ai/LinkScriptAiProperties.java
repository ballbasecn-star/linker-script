package com.linkscript.infra.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "linkscript.ai")
public record LinkScriptAiProperties(
        boolean enabled,
        String apiKey,
        String baseUrl,
        String chatModel,
        String embeddingModel,
        Double temperature
) {
}
