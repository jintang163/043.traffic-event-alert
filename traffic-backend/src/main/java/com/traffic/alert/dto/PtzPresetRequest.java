package com.traffic.alert.dto;

import lombok.Data;

@Data
public class PtzPresetRequest {
    private Long cameraId;
    private Integer presetIndex;
    private String presetName;
    private String thumbnailUrl;
    private Integer sortOrder;
}
