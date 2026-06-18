package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("global_track")
public class GlobalTrack extends BaseEntity {

    private String trackNo;
    private String targetClass;
    private String licensePlate;
    private BigDecimal plateConfidence;
    private String color;
    private String vehicleType;
    private String reidFeature;
    private Long firstCameraId;
    private String firstCameraName;
    private Long lastCameraId;
    private String lastCameraName;
    private BigDecimal firstLongitude;
    private BigDecimal firstLatitude;
    private BigDecimal lastLongitude;
    private BigDecimal lastLatitude;
    private LocalDateTime firstSeenTime;
    private LocalDateTime lastSeenTime;
    private Integer cameraCount;
    private Integer pointCount;
    private BigDecimal totalDistance;
    private BigDecimal avgSpeed;
    private Integer trackStatus;
    private Integer isEventTarget;
    private Integer linkedEventCount;
    private String snapshotUrl;
    private String description;
}
