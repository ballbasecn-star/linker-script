package com.linkscript.infra.logging;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LinkScriptLoggingProperties.class)
public class LoggingPropertiesConfig {
}
