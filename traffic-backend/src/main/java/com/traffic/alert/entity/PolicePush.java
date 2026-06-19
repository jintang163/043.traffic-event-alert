package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("police_push")
public class PolicePush extends BaseEntity {

    private String pushNo;

    private Long alertEventId;

    private String eventNo;

    private Long plateRecognitionId;

    private String eventType;

    private Integer eventLevel;

    private String plateNumber;

    private String plateColor;

    private String vehicleType;

    private String location;

    private Long cameraId;

    private String cameraName;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private LocalDateTime eventTime;

    private Integer pushStatus;

    private Integer retryCount;

    private Integer maxRetry;

    private LocalDateTime nextRetryTime;

    private String pushTarget;

    private String pushBody;

    private String responseBody;

    private String errorMessage;

    private Long costMs;

    private LocalDateTime pushTime;

    private LocalDateTime successTime;
}
