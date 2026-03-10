package com.linkscript.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiGatewayTest {

    @Test
    void shouldReturnEmptyWhenDisabled() {
        LinkScriptAiProperties properties = new LinkScriptAiProperties(
                false, null, null, null, null, null, null, null, null);
        AiGateway gateway = new AiGateway(
                org.springframework.web.client.RestClient.builder(),
                properties);

        assertThat(gateway.chat("system", "user")).isEmpty();
        assertThat(gateway.embed("text")).isEmpty();
    }

    @Test
    void shouldReportDisabledWhenApiKeyMissing() {
        LinkScriptAiProperties properties = new LinkScriptAiProperties(
                true, "", "https://api.example.com/v1", "model", "embed", 0.7, 5, 10, 1);
        AiGateway gateway = new AiGateway(
                org.springframework.web.client.RestClient.builder(),
                properties);

        assertThat(gateway.isEnabled()).isFalse();
        assertThat(gateway.chat("system", "user")).isEmpty();
    }

    @Test
    void shouldResolveDefaultTimeoutAndRetry() {
        LinkScriptAiProperties properties = new LinkScriptAiProperties(
                true, "key", "https://api.example.com/v1", "model", "embed", 0.7, null, null, null);
        assertThat(properties.resolvedConnectTimeoutSeconds()).isEqualTo(10);
        assertThat(properties.resolvedReadTimeoutSeconds()).isEqualTo(60);
        assertThat(properties.resolvedMaxRetries()).isEqualTo(2);
    }

    @Test
    void shouldUseCustomTimeoutAndRetry() {
        LinkScriptAiProperties properties = new LinkScriptAiProperties(
                true, "key", "https://api.example.com/v1", "model", "embed", 0.7, 5, 30, 3);
        assertThat(properties.resolvedConnectTimeoutSeconds()).isEqualTo(5);
        assertThat(properties.resolvedReadTimeoutSeconds()).isEqualTo(30);
        assertThat(properties.resolvedMaxRetries()).isEqualTo(3);
    }
}
