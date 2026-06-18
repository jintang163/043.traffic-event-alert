package com.traffic.alert.rule.controller;

import com.traffic.alert.common.Result;
import com.traffic.alert.rule.dto.RuleExecuteRequest;
import com.traffic.alert.rule.dto.RuleExecuteResult;
import com.traffic.alert.rule.entity.RuleBranch;
import com.traffic.alert.rule.entity.RuleExecutionLog;
import com.traffic.alert.rule.entity.RuleSet;
import com.traffic.alert.rule.service.RuleEngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "规则引擎管理")
@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
public class RuleEngineController {

    private final RuleEngineService ruleEngineService;

    @Operation(summary = "执行规则")
    @PostMapping("/execute")
    public Result<RuleExecuteResult> execute(@RequestBody RuleExecuteRequest request) {
        return Result.success(ruleEngineService.execute(request));
    }

    @Operation(summary = "保存规则集")
    @PostMapping("/sets")
    public Result<RuleSet> saveRuleSet(@RequestBody RuleSet ruleSet) {
        return Result.success(ruleEngineService.saveRuleSet(ruleSet));
    }

    @Operation(summary = "获取规则集详情")
    @GetMapping("/sets/{id}")
    public Result<RuleSet> getRuleSetById(@PathVariable Long id) {
        return Result.success(ruleEngineService.getRuleSetById(id));
    }

    @Operation(summary = "根据编码获取规则集")
    @GetMapping("/sets/code/{ruleCode}")
    public Result<RuleSet> getRuleSetByCode(@PathVariable String ruleCode) {
        return Result.success(ruleEngineService.getRuleSetByCode(ruleCode));
    }

    @Operation(summary = "删除规则集")
    @DeleteMapping("/sets/{id}")
    public Result<Void> deleteRuleSet(@PathVariable Long id) {
        ruleEngineService.deleteRuleSet(id);
        return Result.success();
    }

    @Operation(summary = "获取规则集分支列表")
    @GetMapping("/sets/{ruleSetId}/branches")
    public Result<List<RuleBranch>> getBranches(@PathVariable Long ruleSetId) {
        return Result.success(ruleEngineService.getBranchesByRuleSetId(ruleSetId));
    }

    @Operation(summary = "保存规则分支")
    @PostMapping("/branches")
    public Result<RuleBranch> saveBranch(@RequestBody RuleBranch branch) {
        return Result.success(ruleEngineService.saveBranch(branch));
    }

    @Operation(summary = "删除规则分支")
    @DeleteMapping("/branches/{id}")
    public Result<Void> deleteBranch(@PathVariable Long id) {
        ruleEngineService.deleteBranch(id);
        return Result.success();
    }

    @Operation(summary = "查询执行日志")
    @GetMapping("/logs")
    public Result<List<RuleExecutionLog>> getExecutionLogs(
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(ruleEngineService.getExecutionLogs(ruleCode, startTime, endTime));
    }

    @Operation(summary = "获取执行日志详情")
    @GetMapping("/logs/{executionId}")
    public Result<RuleExecutionLog> getExecutionLog(@PathVariable String executionId) {
        return Result.success(ruleEngineService.getExecutionLog(executionId));
    }
}
