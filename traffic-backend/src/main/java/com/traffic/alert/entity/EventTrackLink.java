package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("event_track_link")
public class EventTrackLink extends BaseEntity {

    private Long eventId;
    private String eventNo;
    private Long trackId;
    private String trackNo;
    private Integer linkType;
    private BigDecimal linkConfidence;
    private Long cameraId;
    private Long trackPointId;
    private String description;
}
