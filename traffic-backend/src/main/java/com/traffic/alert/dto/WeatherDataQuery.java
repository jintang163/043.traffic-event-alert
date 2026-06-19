package com.traffic.alert.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class WeatherDataQuery extends PageQuery {

    private String locationCode;
    private String weatherType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
