package com.traffic.alert.dto;

import lombok.Data;

import java.util.List;

@Data
public class EdgeEventAckRequest {

    private String nodeCode;

    private String token;

    private List<String> eventUuids;
}
