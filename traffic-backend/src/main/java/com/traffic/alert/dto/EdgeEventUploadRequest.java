package com.traffic.alert.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class EdgeEventUploadRequest {

    private String nodeCode;

    private String token;

    private String eventUuid;

    private String eventType;

    private String eventData;

    private LocalDateTime eventTime;

    private Long cameraId;

    private String cameraCode;

    private String cameraName;

    private Integer eventLevel;

    private BigDecimal confidence;

    private String description;

    private String location;

    private BigDecimal longitude;

    private BigDecimal latitude;
}
