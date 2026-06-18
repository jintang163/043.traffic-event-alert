package com.traffic.alert.rule.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class RuleExecuteRequest {

    private String ruleCode;

    private Long ruleSetId;

    private Map<String, Object> formData;

    private Map<String, Object> systemVariables;

    private Map<String, Object> context;
}
