package com.traffic.alert.controller;

import com.traffic.alert.common.PageResult;
import com.traffic.alert.common.Result;
import com.traffic.alert.dto.NotifyRuleQuery;
import com.traffic.alert.entity.NotifyRule;
import com.traffic.alert.service.NotifyRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "通知推送规则")
@RestController
@RequestMapping("/api/notify/rules")
@RequiredArgsConstructor
public class NotifyRuleController {

    private final NotifyRuleService notifyRuleService;

    @Operation(summary = "分页查询推送规则")
    @GetMapping("/page")
    public Result<PageResult<NotifyRule>> page(NotifyRuleQuery query) {
        return Result.success(notifyRuleService.page(query));
    }

    @Operation(summary = "获取规则详情")
    @GetMapping("/{id}")
    public Result<NotifyRule> getById(@PathVariable Long id) {
        return Result.success(notifyRuleService.getById(id));
    }

    @Operation(summary = "保存推送规则")
    @PostMapping
    public Result<NotifyRule> save(@RequestBody NotifyRule rule) {
        return Result.success(notifyRuleService.save(rule));
    }

    @Operation(summary = "删除推送规则")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        notifyRuleService.delete(id);
        return Result.success(null);
    }
}
