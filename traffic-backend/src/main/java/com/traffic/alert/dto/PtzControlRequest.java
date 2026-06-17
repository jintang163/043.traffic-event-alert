package com.traffic.alert.dto;

import lombok.Data;

@Data
public class PtzControlRequest {

    private String command;
    private Integer speed;
    private Integer presetIndex;
}
