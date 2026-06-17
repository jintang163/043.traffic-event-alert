package com.traffic.alert.controller;

import com.traffic.alert.common.PageResult;
import com.traffic.alert.common.Result;
import com.traffic.alert.dto.AlertEventQuery;
import com.traffic.alert.dto.FalsePositiveRequest;
import com.traffic.alert.entity.AlertEvent;
import com.traffic.alert.service.AlertEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Tag(name = "告警事件管理")
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertEventController {

    private final AlertEventService alertEventService;

    @Operation(summary = "分页查询告警事件")
    @GetMapping("/page")
    public Result<PageResult<AlertEvent>> page(AlertEventQuery query) {
        return Result.success(alertEventService.page(query));
    }

    @Operation(summary = "获取告警详情")
    @GetMapping("/{id}")
    public Result<AlertEvent> getById(@PathVariable Long id) {
        return Result.success(alertEventService.getById(id));
    }

    @Operation(summary = "标记已处理")
    @PostMapping("/{id}/handle")
    public Result<AlertEvent> markAsHandled(@PathVariable Long id, @RequestParam(required = false) String remark) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = auth != null && auth.getPrincipal() instanceof Long ? (Long) auth.getPrincipal() : null;
        return Result.success(alertEventService.markAsHandled(id, userId, remark));
    }

    @Operation(summary = "标记误报")
    @PostMapping("/{id}/false-positive")
    public Result<AlertEvent> markAsFalsePositive(@PathVariable Long id, @Valid @RequestBody FalsePositiveRequest request) {
        return Result.success(alertEventService.markAsFalsePositive(id, request));
    }

    @Operation(summary = "获取统计信息")
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics() {
        return Result.success(alertEventService.getStatistics());
    }

    @Operation(summary = "获取最近告警")
    @GetMapping("/recent")
    public Result<List<AlertEvent>> getRecentEvents(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(alertEventService.getRecentEvents(limit));
    }

    @Operation(summary = "上传告警快照")
    @PostMapping("/{id}/snapshot")
    public Result<String> uploadSnapshot(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return Result.success(alertEventService.uploadEventSnapshot(id, file));
    }
}
