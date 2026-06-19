package com.traffic.alert.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AiLicensePlateDto {
    private String plateNumber;
    private Float confidence;
    private String plateColor;
    private String vehicleColor;
    private String vehicleType;
    private List<Float> bbox;
    private Integer trackId;
    private String sceneType;
    private Float enhanceGain;
    private Map<String, Object> extra;
}
