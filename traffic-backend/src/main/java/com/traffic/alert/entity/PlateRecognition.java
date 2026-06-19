package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("plate_recognition")
public class PlateRecognition extends BaseEntity {

    private String recognizeNo;

    private Long alertEventId;

    private String eventNo;

    private Long cameraId;

    private String cameraName;

    private String plateNumber;

    private String plateColor;

    private String vehicleColor;

    private String vehicleType;

    private BigDecimal confidence;

    private String sceneType;

    private BigDecimal enhanceGain;

    private Integer trackId;

    private Integer bboxX1;

    private Integer bboxY1;

    private Integer bboxX2;

    private Integer bboxY2;

    private String plateImageUrl;

    private String fullImageUrl;

    private LocalDateTime recognizeTime;
}
