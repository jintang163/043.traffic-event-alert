package com.traffic.alert.dto;

import lombok.Data;

@Data
public class VideoQualityManualDetectRequest {

    private Long cameraId;

    private String streamUrl;

    private Integer frameCount;
}
