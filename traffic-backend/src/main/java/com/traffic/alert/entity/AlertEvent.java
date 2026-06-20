package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("alert_event")
public class AlertEvent extends BaseEntity {

    private String eventNo;

    private String eventType;

    private String debrisCategory;

    private Integer eventLevel;

    private Long cameraId;

    private String cameraName;

    private String location;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private LocalDateTime eventTime;

    private BigDecimal confidence;

    private String eventSnapshot;

    private String eventVideo;

    private String description;

    private Integer alertStatus;

    private Integer isFalsePositive;

    private String falsePositiveReason;

    private Long handleUserId;

    private LocalDateTime handleTime;

    private String handleRemark;

    private Integer accidentVehicles;

    private Integer accidentDeformationLevel;

    private Integer accidentRollover;

    private Integer accidentFire;

    private Integer accidentCasualty;

    private BigDecimal accidentImpactSpeed;

    private String accidentSeverity;

    private String accidentSeverityLabel;

    private Integer accidentPriority;

    private String accidentEvaluationReasons;

    private String sourceNodeCode;

    private Long constructionPlanId;

    @TableField(exist = false)
    private Map<String, Object> eventMetadata;
}
