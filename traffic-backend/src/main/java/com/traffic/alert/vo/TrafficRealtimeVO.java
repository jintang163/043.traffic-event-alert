package com.traffic.alert.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TrafficRealtimeVO {

    private Long cameraId;

    private String cameraName;

    private String roadName;

    private Integer laneNo;

    private String laneName;

    private LocalDateTime timestamp;

    private Integer flowVolume;

    private BigDecimal avgSpeed;

    private BigDecimal occupancy;

    private BigDecimal density;

    private String level;

    private String levelName;
}
