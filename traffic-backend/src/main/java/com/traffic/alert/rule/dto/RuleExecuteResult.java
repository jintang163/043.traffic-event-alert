package com.traffic.alert.rule.dto;

import com.traffic.alert.rule.entity.RuleBranch;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class RuleExecuteResult {

    private String executionId;

    private Long ruleSetId;

    private String ruleCode;

    private String ruleName;

    private Integer gatewayType;

    private String gatewayTypeName;

    private List<RuleBranch> matchedBranches;

    private List<RuleBranch> allBranches;

    private Map<String, Object> inputContext;

    private Long executionTime;

    private Boolean success;

    private String errorMessage;
}
