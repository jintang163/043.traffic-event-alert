package com.traffic.alert.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai.engine")
public class AiEngineConfig {

    private String baseUrl = "http://localhost:8000";
    private String detectPath = "/api/v1/detect/image";
    private String streamPath = "/api/v1/detect/stream";
    private String trackPath = "/api/v1/track";
    private String eventPath = "/api/v1/event/analyze";
    private Integer connectTimeout = 5000;
    private Integer readTimeout = 30000;
}
