package com.linkscript.infra.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "linkscript.vector")
public record VectorProperties(int dimensions, int lexicalCandidateLimit) {
}
