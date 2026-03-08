package com.linkscript.infra.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({LinkScriptAiProperties.class, VectorProperties.class})
public class PropertiesConfig {
}
