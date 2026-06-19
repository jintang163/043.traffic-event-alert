package com.traffic.alert.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EdgeEventUploadRequest {

    private String nodeCode;

    private String token;

    private String eventUuid;

    private String eventType;

    private String eventData;

    private LocalDateTime eventTime;

    private String cameraCode;

    private String cameraName;
}
