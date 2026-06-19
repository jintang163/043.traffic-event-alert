package com.traffic.alert.dto;

import lombok.Data;

import java.util.List;

@Data
public class EdgeEventBatchUploadRequest {

    private String nodeCode;

    private String token;

    private List<EdgeEventUploadRequest> events;
}
