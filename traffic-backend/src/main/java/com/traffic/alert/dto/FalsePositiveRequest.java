package com.traffic.alert.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
public class FalsePositiveRequest {

    @NotBlank(message = "误报原因不能为空")
    private String reason;
}
