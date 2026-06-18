package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("track_point")
public class TrackPoint extends BaseEntity {

    private Long trackId;
    private Long cameraId;
    private String cameraName;
    private Long frameNo;
    private LocalDateTime frameTime;
    private Integer bboxX1;
    private Integer bboxY1;
    private Integer bboxX2;
    private Integer bboxY2;
    private BigDecimal bboxConfidence;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private BigDecimal pixelX;
    private BigDecimal pixelY;
    private BigDecimal velocityX;
    private BigDecimal velocityY;
    private BigDecimal speed;
    private BigDecimal direction;
    private String reidFeature;
    private String snapshotUrl;
    private Integer isKeyPoint;
    private Integer keyPointType;
}
