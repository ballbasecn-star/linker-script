package com.linkscript.infra.logging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "linkscript.logging")
public record LinkScriptLoggingProperties(boolean enabled, int maxPayloadLength) {
}
