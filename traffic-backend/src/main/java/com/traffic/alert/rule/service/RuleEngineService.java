package com.traffic.alert.rule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.traffic.alert.common.BusinessException;
import com.traffic.alert.rule.dto.RuleExecuteRequest;
import com.traffic.alert.rule.dto.RuleExecuteResult;
import com.traffic.alert.rule.entity.RuleBranch;
import com.traffic.alert.rule.entity.RuleExecutionLog;
import com.traffic.alert.rule.entity.RuleSet;
import com.traffic.alert.rule.enums.GatewayType;
import com.traffic.alert.rule.mapper.RuleBranchMapper;
import com.traffic.alert.rule.mapper.RuleExecutionLogMapper;
import com.traffic.alert.rule.mapper.RuleSetMapper;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuleEngineService {

    private final RuleSetMapper ruleSetMapper;
    private final RuleBranchMapper ruleBranchMapper;
    private final RuleExecutionLogMapper ruleExecutionLogMapper;
    private final ExpressionEngineService expressionEngineService;

    @Transactional
    public RuleExecuteResult execute(RuleExecuteRequest request) {
        long startTime = System.currentTimeMillis();
        String executionId = UUID.randomUUID().toString().replace("-", "");

        RuleExecuteResult result = new RuleExecuteResult();
        result.setExecutionId(executionId);

        RuleExecutionLog executionLog = new RuleExecutionLog();
        executionLog.setExecutionId(executionId);

        try {
            RuleSet ruleSet = resolveRuleSet(request);
            if (ruleSet == null) {
                throw new BusinessException("规则集不存在");
            }

            result.setRuleSetId(ruleSet.getId());
            result.setRuleCode(ruleSet.getRuleCode());
            result.setRuleName(ruleSet.getRuleName());
            result.setGatewayType(ruleSet.getGatewayType());
            result.setGatewayTypeName(GatewayType.of(ruleSet.getGatewayType()).getName());

            executionLog.setRuleSetId(ruleSet.getId());
            executionLog.setRuleCode(ruleSet.getRuleCode());
            executionLog.setRuleName(ruleSet.getRuleName());
            executionLog.setGatewayType(ruleSet.getGatewayType());

            List<RuleBranch> allBranches = ruleBranchMapper.selectList(
                    new LambdaQueryWrapper<RuleBranch>()
                            .eq(RuleBranch::getRuleSetId, ruleSet.getId())
                            .orderByAsc(RuleBranch::getSortOrder, RuleBranch::getPriority)
            );
            result.setAllBranches(allBranches);

            Map<String, Object> context = buildContext(request);
            result.setInputContext(context);
            executionLog.setInputContext(JSON.toJSONString(context));

            GatewayType gatewayType = GatewayType.of(ruleSet.getGatewayType());
            List<RuleBranch> matchedBranches = evaluateBranches(gatewayType, allBranches, context, ruleSet.getDefaultBranch());
            result.setMatchedBranches(matchedBranches);

            String matchedBranchCodes = matchedBranches.stream()
                    .map(RuleBranch::getBranchCode)
                    .collect(Collectors.joining(","));
            executionLog.setMatchedBranches(matchedBranchCodes);
            executionLog.setExecutionResult(JSON.toJSONString(matchedBranches));

            result.setSuccess(true);
            executionLog.setSuccess(1);

            log.info("规则执行完成: ruleCode={}, gatewayType={}, matchedBranches={}, executionId={}",
                    ruleSet.getRuleCode(), gatewayType.getName(), matchedBranchCodes, executionId);

        } catch (BusinessException e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            executionLog.setSuccess(0);
            executionLog.setErrorMessage(e.getMessage());
            log.warn("规则执行业务异常: {}, executionId={}", e.getMessage(), executionId);
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage("规则执行异常: " + e.getMessage());
            executionLog.setSuccess(0);
            executionLog.setErrorMessage(e.getMessage());
            log.error("规则执行异常: {}, executionId={}", e.getMessage(), executionId, e);
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            result.setExecutionTime(executionTime);
            executionLog.setExecutionTime(executionTime);
            try {
                ruleExecutionLogMapper.insert(executionLog);
            } catch (Exception e) {
                log.error("保存规则执行日志失败: {}", e.getMessage(), e);
            }
        }

        return result;
    }

    private RuleSet resolveRuleSet(RuleExecuteRequest request) {
        if (request.getRuleSetId() != null) {
            return ruleSetMapper.selectById(request.getRuleSetId());
        }
        if (request.getRuleCode() != null && !request.getRuleCode().isEmpty()) {
            return ruleSetMapper.selectOne(
                    new LambdaQueryWrapper<RuleSet>()
                            .eq(RuleSet::getRuleCode, request.getRuleCode())
                            .last("LIMIT 1")
            );
        }
        return null;
    }

    private Map<String, Object> buildContext(RuleExecuteRequest request) {
        Map<String, Object> context = expressionEngineService.buildDefaultContext();

        if (request.getContext() != null) {
            context.putAll(request.getContext());
        }
        if (request.getFormData() != null) {
            context.put("form", request.getFormData());
        }
        if (request.getSystemVariables() != null) {
            context.put("system", request.getSystemVariables());
        }

        return context;
    }

    private List<RuleBranch> evaluateBranches(GatewayType gatewayType,
                                               List<RuleBranch> allBranches,
                                               Map<String, Object> context,
                                               String defaultBranch) {
        List<RuleBranch> matchedBranches = new ArrayList<>();

        switch (gatewayType) {
            case EXCLUSIVE:
                RuleBranch matched = evaluateExclusive(allBranches, context);
                if (matched != null) {
                    matchedBranches.add(matched);
                } else if (defaultBranch != null && !defaultBranch.isEmpty()) {
                    allBranches.stream()
                            .filter(b -> defaultBranch.equals(b.getBranchCode()))
                            .findFirst()
                            .ifPresent(matchedBranches::add);
                }
                break;

            case PARALLEL:
                matchedBranches.addAll(allBranches);
                break;

            case INCLUSIVE:
                for (RuleBranch branch : allBranches) {
                    if (evaluateBranch(branch, context)) {
                        matchedBranches.add(branch);
                    }
                }
                if (matchedBranches.isEmpty() && defaultBranch != null && !defaultBranch.isEmpty()) {
                    allBranches.stream()
                            .filter(b -> defaultBranch.equals(b.getBranchCode()))
                            .findFirst()
                            .ifPresent(matchedBranches::add);
                }
                break;
        }

        return matchedBranches;
    }

    private RuleBranch evaluateExclusive(List<RuleBranch> branches, Map<String, Object> context) {
        List<RuleBranch> sorted = branches.stream()
                .sorted(Comparator.comparing(RuleBranch::getSortOrder)
                        .thenComparing(RuleBranch::getPriority, Comparator.reverseOrder()))
                .collect(Collectors.toList());

        for (RuleBranch branch : sorted) {
            if (evaluateBranch(branch, context)) {
                return branch;
            }
        }
        return null;
    }

    private boolean evaluateBranch(RuleBranch branch, Map<String, Object> context) {
        String expression = branch.getExpression();
        if (expression == null || expression.trim().isEmpty()) {
            return true;
        }
        try {
            Boolean result = expressionEngineService.executeBoolean(expression, context);
            log.debug("分支评估: branchCode={}, expression={}, result={}", branch.getBranchCode(), expression, result);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("分支评估异常: branchCode={}, expression={}, error={}",
                    branch.getBranchCode(), expression, e.getMessage());
            return false;
        }
    }

    public RuleSet saveRuleSet(RuleSet ruleSet) {
        if (ruleSet.getId() == null) {
            ruleSetMapper.insert(ruleSet);
        } else {
            ruleSetMapper.updateById(ruleSet);
        }
        return ruleSet;
    }

    public RuleSet getRuleSetById(Long id) {
        return ruleSetMapper.selectById(id);
    }

    public RuleSet getRuleSetByCode(String ruleCode) {
        return ruleSetMapper.selectOne(
                new LambdaQueryWrapper<RuleSet>()
                        .eq(RuleSet::getRuleCode, ruleCode)
                        .last("LIMIT 1")
        );
    }

    public void deleteRuleSet(Long id) {
        ruleSetMapper.deleteById(id);
        ruleBranchMapper.delete(new LambdaQueryWrapper<RuleBranch>().eq(RuleBranch::getRuleSetId, id));
    }

    public List<RuleBranch> getBranchesByRuleSetId(Long ruleSetId) {
        return ruleBranchMapper.selectList(
                new LambdaQueryWrapper<RuleBranch>()
                        .eq(RuleBranch::getRuleSetId, ruleSetId)
                        .orderByAsc(RuleBranch::getSortOrder, RuleBranch::getPriority)
        );
    }

    public RuleBranch saveBranch(RuleBranch branch) {
        if (branch.getId() == null) {
            ruleBranchMapper.insert(branch);
        } else {
            ruleBranchMapper.updateById(branch);
        }
        return branch;
    }

    public void deleteBranch(Long id) {
        ruleBranchMapper.deleteById(id);
    }

    public List<RuleExecutionLog> getExecutionLogs(String ruleCode, LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<RuleExecutionLog> wrapper = new LambdaQueryWrapper<>();
        if (ruleCode != null && !ruleCode.isEmpty()) {
            wrapper.eq(RuleExecutionLog::getRuleCode, ruleCode);
        }
        if (startTime != null) {
            wrapper.ge(RuleExecutionLog::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(RuleExecutionLog::getCreateTime, endTime);
        }
        wrapper.orderByDesc(RuleExecutionLog::getCreateTime);
        return ruleExecutionLogMapper.selectList(wrapper);
    }

    public RuleExecutionLog getExecutionLog(String executionId) {
        return ruleExecutionLogMapper.selectOne(
                new LambdaQueryWrapper<RuleExecutionLog>()
                        .eq(RuleExecutionLog::getExecutionId, executionId)
                        .last("LIMIT 1")
        );
    }
}
