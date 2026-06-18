package com.traffic.alert.rule.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.traffic.alert.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("rule_branch")
public class RuleBranch extends BaseEntity {

    private Long ruleSetId;

    private String branchCode;

    private String branchName;

    private String expression;

    private String actionType;

    private String actionTarget;

    private String actionParams;

    private BigDecimal priority;

    private Integer sortOrder;
}
