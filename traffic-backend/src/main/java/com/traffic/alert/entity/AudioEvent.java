package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("audio_event")
public class AudioEvent extends BaseEntity {

    private String eventNo;

    private Long cameraId;

    private String cameraName;

    private String eventType;

    private BigDecimal confidence;

    private BigDecimal duration;

    private BigDecimal peakDb;

    private BigDecimal avgDb;

    private BigDecimal dominantFreq;

    private LocalDateTime eventTime;

    private String description;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String location;

    private Integer alertStatus;

    private Long linkedAlertEventId;

    private BigDecimal ambientDb;

    private String audioClipUrl;
}
