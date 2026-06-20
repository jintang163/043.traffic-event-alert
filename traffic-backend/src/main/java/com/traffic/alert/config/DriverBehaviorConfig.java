package com.traffic.alert.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "driver.behavior")
public class DriverBehaviorConfig {

    private Boolean enabled = true;

    private Boolean enableRealFrameCapture = true;

    private Integer detectionIntervalMinutes = 5;

    private String ffmpegPath = "ffmpeg";

    private Integer frameCaptureTimeoutSeconds = 10;

    private Integer frameMaxWidth = 640;

    private Thresholds thresholds = new Thresholds();

    private Scoring scoring = new Scoring();

    private AlertRules alertRules = new AlertRules();

    @Data
    public static class Thresholds {
        private BigDecimal phoneCallConfidence = new java.math.BigDecimal("70.00");
        private BigDecimal yawningConfidence = new java.math.BigDecimal("70.00");
        private BigDecimal mouthOpenRatio = new java.math.BigDecimal("0.45");
        private BigDecimal fatigueConfidence = new java.math.BigDecimal("65.00");
        private BigDecimal eyeAspectRatio = new java.math.BigDecimal("0.20");
        private BigDecimal perclosScore = new java.math.BigDecimal("50.00");
        private BigDecimal distractionConfidence = new java.math.BigDecimal("65.00");
        private BigDecimal headPoseYawThreshold = new java.math.BigDecimal("25.00");
        private BigDecimal headPosePitchThreshold = new java.math.BigDecimal("15.00");
        private Integer consecutiveAbnormalThreshold = 3;
        private Integer alertCooldownSeconds = 180;
    }

    @Data
    public static class Scoring {
        private BigDecimal phoneCallWeight = new java.math.BigDecimal("30");
        private BigDecimal yawningWeight = new java.math.BigDecimal("25");
        private BigDecimal fatigueWeight = new java.math.BigDecimal("25");
        private BigDecimal distractionWeight = new java.math.BigDecimal("20");
    }

    @Data
    public static class AlertRules {
        private Boolean phoneCallAlert = true;
        private Boolean yawningAlert = true;
        private Boolean fatigueAlert = true;
        private Boolean distractionAlert = true;
        private Integer phoneCallLevel = 3;
        private Integer yawningLevel = 2;
        private Integer fatigueLevel = 4;
        private Integer distractionLevel = 2;
        private Boolean ledReminderEnabled = true;
        private Integer ledDisplaySeconds = 30;
    }
}
