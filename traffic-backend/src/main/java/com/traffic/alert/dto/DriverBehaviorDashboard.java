package com.traffic.alert.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class DriverBehaviorDashboard {

    private Integer totalInCarCameras;

    private Integer monitoredCount;

    private Integer todayDetectionCount;

    private Integer todayAbnormalCount;

    private BigDecimal avgBehaviorScore;

    private Long phoneCallCount;

    private Long yawningCount;

    private Long fatigueCount;

    private Long distractionCount;

    private Map<String, Long> abnormalTypeStats;

    private List<DriverBehaviorAbnormalRecord> recentAbnormalRecords;

    private List<DriverBehaviorCameraItem> cameraBehaviorList;

    private String reportDate;
}
