package com.traffic.alert.rule.controller;

import com.traffic.alert.common.Result;
import com.traffic.alert.rule.entity.DecisionTable;
import com.traffic.alert.rule.mapper.DecisionTableMapper;
import com.traffic.alert.rule.service.DecisionTableService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "决策表管理")
@RestController
@RequestMapping("/api/decision-tables")
@RequiredArgsConstructor
public class DecisionTableController {

    private final DecisionTableService decisionTableService;
    private final DecisionTableMapper decisionTableMapper;

    @Operation(summary = "解析决策表")
    @PostMapping("/parse")
    public Result<List<DecisionTableService.DecisionRule>> parse(@RequestBody Map<String, String> body) {
        String tableData = body.get("tableData");
        return Result.success(decisionTableService.parseTable(tableData));
    }

    @Operation(summary = "评估决策表")
    @PostMapping("/evaluate")
    public Result<List<DecisionTableService.DecisionRule>> evaluate(@RequestBody Map<String, Object> body) {
        String tableData = (String) body.get("tableData");
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) body.getOrDefault("context", Map.of());
        String hitPolicy = (String) body.getOrDefault("hitPolicy", "FIRST");
        return Result.success(decisionTableService.evaluate(tableData, context, hitPolicy));
    }

    @Operation(summary = "评估决策表并返回合并结果")
    @PostMapping("/evaluate-result")
    public Result<Map<String, Object>> evaluateToResult(@RequestBody Map<String, Object> body) {
        String tableData = (String) body.get("tableData");
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) body.getOrDefault("context", Map.of());
        String hitPolicy = (String) body.getOrDefault("hitPolicy", "FIRST");
        return Result.success(decisionTableService.evaluateToResult(tableData, context, hitPolicy));
    }

    @Operation(summary = "保存决策表")
    @PostMapping
    public Result<DecisionTable> save(@RequestBody DecisionTable decisionTable) {
        if (decisionTable.getId() == null) {
            decisionTableMapper.insert(decisionTable);
        } else {
            decisionTableMapper.updateById(decisionTable);
        }
        return Result.success(decisionTable);
    }

    @Operation(summary = "获取决策表详情")
    @GetMapping("/{id}")
    public Result<DecisionTable> getById(@PathVariable Long id) {
        return Result.success(decisionTableMapper.selectById(id));
    }

    @Operation(summary = "获取决策表列表")
    @GetMapping
    public Result<List<DecisionTable>> list() {
        return Result.success(decisionTableMapper.selectList(
                new LambdaQueryWrapper<DecisionTable>().orderByDesc(DecisionTable::getCreateTime)
        ));
    }

    @Operation(summary = "删除决策表")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        decisionTableMapper.deleteById(id);
        return Result.success();
    }
}
