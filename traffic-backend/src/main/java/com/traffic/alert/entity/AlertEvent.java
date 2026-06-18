package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
}
