package com.traffic.alert.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EdgeNodeRegisterRequest {

    private String nodeCode;

    private String nodeName;

    private String hardwareModel;

    private String gpuInfo;

    private Integer cpuCores;

    private Integer memoryGB;

    private Integer storageGB;

    private String osInfo;

    private String ipAddress;

    private String macAddress;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String location;

    private Integer heartbeatInterval;

    private Integer cameraCount;

    private String token;
}
