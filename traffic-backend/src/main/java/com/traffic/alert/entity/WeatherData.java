package com.traffic.alert.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("weather_data")
public class WeatherData extends BaseEntity {

    private LocalDateTime recordTime;

    private String locationCode;

    private String locationName;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String weatherType;

    private BigDecimal temperature;

    private BigDecimal humidity;

    private BigDecimal windSpeed;

    private Integer windDirection;

    private BigDecimal visibility;

    private BigDecimal precipitation;
}
