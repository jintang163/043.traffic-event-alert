package com.traffic.alert.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Data
@Configuration
@ConfigurationProperties(prefix = "video.quality")
public class VideoQualityConfig {

    private Boolean enabled = true;

    private Integer detectionIntervalMinutes = 15;

    private Integer concurrentDetectionLimit = 10;

    private Integer abnormalTriggerThreshold = 3;

    private Integer alertCooldownMinutes = 30;

    private Thresholds thresholds = new Thresholds();

    private Scoring scoring = new Scoring();

    @Data
    public static class Thresholds {

        private BigDecimal blackScreenBrightness = BigDecimal.valueOf(15);

        private BigDecimal lowBrightness = BigDecimal.valueOf(40);

        private BigDecimal highBrightness = BigDecimal.valueOf(220);

        private BigDecimal lowContrast = BigDecimal.valueOf(35);

        private BigDecimal highContrast = BigDecimal.valueOf(95);

        private BigDecimal severeBlur = BigDecimal.valueOf(0.35);

        private BigDecimal slightBlur = BigDecimal.valueOf(0.65);

        private BigDecimal severeOcclusion = BigDecimal.valueOf(30);

        private BigDecimal slightOcclusion = BigDecimal.valueOf(10);

        private BigDecimal freezeFrameChange = BigDecimal.valueOf(0.02);

        private Integer freezeMinFrames = 5;

        private BigDecimal severeColorCast = BigDecimal.valueOf(0.25);

        private BigDecimal slightColorCast = BigDecimal.valueOf(0.12);
    }

    @Data
    public static class Scoring {

        private Integer brightnessWeight = 20;

        private Integer contrastWeight = 15;

        private Integer blurWeight = 25;

        private Integer occlusionWeight = 20;

        private Integer freezeWeight = 15;

        private Integer noiseWeight = 5;

        private Integer excellentMin = 90;

        private Integer goodMin = 75;

        private Integer mediumMin = 60;

        private Integer poorMin = 40;

        private BigDecimal healthExcellent = BigDecimal.valueOf(90);

        private BigDecimal healthSubhealthy = BigDecimal.valueOf(75);

        private BigDecimal healthAbnormal = BigDecimal.valueOf(50);

        private BigDecimal healthCritical = BigDecimal.valueOf(25);
    }
}
