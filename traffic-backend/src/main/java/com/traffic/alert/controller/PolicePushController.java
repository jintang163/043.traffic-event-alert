package com.traffic.alert.controller;

import com.traffic.alert.common.PageResult;
import com.traffic.alert.common.Result;
import com.traffic.alert.dto.PolicePushQuery;
import com.traffic.alert.entity.PolicePush;
import com.traffic.alert.service.PolicePushService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "交警系统推送")
@RestController
@RequestMapping("/api/police-pushes")
@RequiredArgsConstructor
public class PolicePushController {

    private final PolicePushService service;

    @Operation(summary = "分页查询推送记录")
    @GetMapping
    public Result<PageResult<PolicePush>> page(PolicePushQuery query) {
        return Result.success(service.page(query));
    }

    @Operation(summary = "根据ID获取推送记录")
    @GetMapping("/{id}")
    public Result<PolicePush> getById(@PathVariable Long id) {
        return Result.success(service.getById(id));
    }

    @Operation(summary = "按事件ID获取推送记录列表")
    @GetMapping("/event/{alertEventId}")
    public Result<List<PolicePush>> listByEventId(@PathVariable Long alertEventId) {
        return Result.success(service.listByAlertEventId(alertEventId));
    }

    @Operation(summary = "手动重试推送")
    @PostMapping("/{id}/retry")
    public Result<PolicePush> retry(@PathVariable Long id) {
        return Result.success(service.manualRetry(id));
    }

    @Operation(summary = "新增推送记录")
    @PostMapping
    public Result<PolicePush> create(@RequestBody PolicePush entity) {
        return Result.success(service.save(entity));
    }

    @Operation(summary = "更新推送记录")
    @PutMapping("/{id}")
    public Result<PolicePush> update(@PathVariable Long id, @RequestBody PolicePush entity) {
        entity.setId(id);
        return Result.success(service.save(entity));
    }

    @Operation(summary = "删除推送记录")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }

    @Operation(summary = "推送统计")
    @GetMapping("/statistics/summary")
    public Result<Map<String, Object>> statistics() {
        return Result.success(service.getStatistics());
    }
}
