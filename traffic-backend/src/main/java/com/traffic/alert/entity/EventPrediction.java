package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("event_prediction")
public class EventPrediction extends BaseEntity {

    private String predictionNo;

    private LocalDateTime predictionTime;

    private LocalDateTime targetStartTime;

    private LocalDateTime targetEndTime;

    private Integer targetHours;

    private Long cameraId;

    private String cameraName;

    private String roadName;

    private BigDecimal longitude;

    private BigDecimal latitude;

    @TableField(exist = false)
    private String geomWkt;

    private BigDecimal riskScore;

    private Integer riskLevel;

    private String riskLevelLabel;

    private String eventType;

    private String eventTypeLabel;

    private BigDecimal probability;

    private Integer historicalEventCount;

    private BigDecimal weatherFactor;

    private BigDecimal timeFactor;

    private BigDecimal holidayFactor;

    private String featureJson;

    private BigDecimal confidence;

    private Integer status;

    private Integer actualEventCount;

    private BigDecimal predictionAccuracy;

    private String description;
}
