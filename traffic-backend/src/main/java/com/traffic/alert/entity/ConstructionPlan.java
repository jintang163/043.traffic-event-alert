package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("construction_plan")
public class ConstructionPlan extends BaseEntity {

    private String planCode;

    private String planName;

    private Integer constructionType;

    private String constructionTypeLabel;

    private Long cameraId;

    private String cameraName;

    private Long fenceId;

    private Long bufferFenceId;

    private String roadName;

    private String location;

    private String startStake;

    private String endStake;

    private BigDecimal centerLongitude;

    private BigDecimal centerLatitude;

    private String polygonPoints;

    private String polygonPointsPixel;

    private BigDecimal bufferDistance;

    private BigDecimal speedLimit;

    private Integer standardConeCount;

    private BigDecimal coneSpacing;

    private LocalDateTime planStartTime;

    private LocalDateTime planEndTime;

    private LocalDateTime actualStartTime;

    private LocalDateTime actualEndTime;

    private String constructionUnit;

    private String responsiblePerson;

    private String contactPhone;

    private Integer workerCount;

    private String equipmentInfo;

    private String trafficControlMeasures;

    private Integer planStatus;

    private String planStatusLabel;

    private Integer alertEnabled;

    private Integer alertLevel;

    private Integer notifyEnabled;

    private String notifyDeptIds;

    private Integer linkWorkOrder;

    private Integer ledReminderEnabled;

    private String ledDefaultMessage;

    private Integer eventCount;

    private Integer coneAlertCount;

    private Integer intrusionAlertCount;

    private Integer speedingAlertCount;

    private String description;

    private Integer sortOrder;
}
