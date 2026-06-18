package com.traffic.alert.rule.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.traffic.alert.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("rule_execution_log")
public class RuleExecutionLog extends BaseEntity {

    private String executionId;

    private Long ruleSetId;

    private String ruleCode;

    private String ruleName;

    private Integer gatewayType;

    private String matchedBranches;

    private String inputContext;

    private String executionResult;

    private Long executionTime;

    private String errorMessage;

    private Integer success;
}
