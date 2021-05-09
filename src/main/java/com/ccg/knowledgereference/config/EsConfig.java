package com.ccg.knowledgereference.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "es")
@Configuration
@Data
public class EsConfig {
    private String host;
    private int port;
}
