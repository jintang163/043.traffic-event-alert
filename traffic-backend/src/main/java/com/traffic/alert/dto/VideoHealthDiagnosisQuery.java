package com.traffic.alert.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
public class VideoHealthDiagnosisQuery extends PageQuery {

    private String keyword;

    private Long cameraId;

    private String cameraCode;

    private String roadName;

    private Integer healthLevel;

    private Integer maintenanceStatus;

    private String periodType;

    private LocalDate startDate;

    private LocalDate endDate;
}
