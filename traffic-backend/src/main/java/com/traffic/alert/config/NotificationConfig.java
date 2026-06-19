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
    private Voice voice = new Voice();
    private Retry retry = new Retry();

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

    @Data
    public static class Voice {
        private String accessKeyId;
        private String accessKeySecret;
        private String ttsTemplateCode;
        private String calledShowNumber;
        private String regionId = "cn-hangzhou";
        private boolean enabled = false;
    }

    @Data
    public static class Retry {
        private int maxRetries = 3;
        private long initialDelaySeconds = 30;
        private long maxDelaySeconds = 300;
        private double multiplier = 2.0;
    }
}
