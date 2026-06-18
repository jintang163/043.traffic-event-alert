package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("track_match_log")
public class TrackMatchLog extends BaseEntity {

    private LocalDateTime matchTime;
    private Long sourceCameraId;
    private Long targetCameraId;
    @TableField("source_track_id")
    private Long sourceCamTrackId;
    private String sourceTrackNo;
    @TableField("target_track_id")
    private Long targetCamTrackId;
    private String targetTrackNo;
    private Long globalTrackId;
    private Integer matchMethod;
    private BigDecimal matchScore;
    private BigDecimal plateMatchScore;
    private BigDecimal reidMatchScore;
    private Integer travelSeconds;
    private Integer expectedSeconds;
    private Integer isSuccess;
    private String reason;
}
