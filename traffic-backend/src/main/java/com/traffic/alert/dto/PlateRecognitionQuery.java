package com.traffic.alert.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PlateRecognitionQuery extends PageQuery {

    private String eventNo;
    private String plateNumber;
    private Long cameraId;
    private String sceneType;
    private String startTime;
    private String endTime;
}
