package com.traffic.alert.dto;

import lombok.Data;

@Data
public class PtzControlRequest {

    private String command;
    private Integer speed;
    private Integer presetIndex;
    private Double pan;
    private Double tilt;
    private Double zoom;
    private String presetName;
    private String action;
}
