package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("driver_behavior_record")
public class DriverBehaviorRecord extends BaseEntity {

    private String recordNo;

    private Long cameraId;

    private String cameraName;

    private String cameraCode;

    private String roadName;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private LocalDateTime detectTime;

    private String algorithmVersion;

    private Integer isPhoneCall;

    private BigDecimal phoneCallConfidence;

    private String phoneCallRegion;

    private Integer isYawning;

    private BigDecimal yawningConfidence;

    private BigDecimal mouthOpenRatio;

    private Integer isFatigued;

    private BigDecimal fatigueConfidence;

    private BigDecimal eyeAspectRatio;

    private BigDecimal perclosScore;

    private Integer isDistracted;

    private BigDecimal distractionConfidence;

    private BigDecimal headPoseYaw;

    private BigDecimal headPosePitch;

    private BigDecimal overallScore;

    private Integer behaviorLevel;

    private Integer isAbnormal;

    private String abnormalTypes;

    private String description;

    private Integer alertTriggered;

    private Long alertEventId;

    private Integer ledReminded;

    private String ledRemindResult;

    private Integer isRealFrame;

    private Long frameCaptureCostMs;

    private Integer detectionDurationMs;

    private String remark;
}
