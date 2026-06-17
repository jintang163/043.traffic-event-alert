package com.traffic.alert.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class AiEventCallbackRequest {

    private Long cameraId;
    private String eventType;
    private Integer eventLevel;
    private BigDecimal confidence;
    private LocalDateTime eventTime;
    private String description;
    private String snapshotBase64;
    private List<Map<String, Object>> trackData;
    private Map<String, Object> bbox;
}
