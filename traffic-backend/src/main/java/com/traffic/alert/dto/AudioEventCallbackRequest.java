package com.traffic.alert.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class AudioEventCallbackRequest {

    private String eventNo;
    private Long cameraId;
    private String eventType;
    private BigDecimal confidence;
    private BigDecimal duration;
    private BigDecimal peakDb;
    private BigDecimal avgDb;
    private BigDecimal dominantFreq;
    private LocalDateTime eventTime;
    private String description;
    private Map<String, Object> metadata;
}
