package com.traffic.alert.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "notification")
public class NotificationConfig {

    private DingTalk dingTalk = new DingTalk();
    private WeChat weChat = new WeChat();
    private Sms sms = new Sms();

    @Data
    public static class DingTalk {
        private String webhook;
        private String secret;
        private boolean enabled = false;
    }

    @Data
    public static class WeChat {
        private String webhook;
        private boolean enabled = false;
    }

    @Data
    public static class Sms {
        private String apiUrl;
        private String apiKey;
        private String templateId;
        private String emergencyTemplateId;
        private boolean enabled = false;
    }
}
