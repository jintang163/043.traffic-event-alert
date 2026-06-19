package com.traffic.alert.controller;

import com.traffic.alert.common.PageResult;
import com.traffic.alert.common.Result;
import com.traffic.alert.dto.NotifyLogQuery;
import com.traffic.alert.entity.NotifyLog;
import com.traffic.alert.service.NotifyLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "通知推送日志")
@RestController
@RequestMapping("/api/notify/logs")
@RequiredArgsConstructor
public class NotifyLogController {

    private final NotifyLogService notifyLogService;

    @Operation(summary = "分页查询推送日志")
    @GetMapping("/page")
    public Result<PageResult<NotifyLog>> page(NotifyLogQuery query) {
        return Result.success(notifyLogService.page(query));
    }

    @Operation(summary = "获取日志详情")
    @GetMapping("/{id}")
    public Result<NotifyLog> getById(@PathVariable Long id) {
        return Result.success(notifyLogService.getById(id));
    }

    @Operation(summary = "手动重试推送")
    @PostMapping("/{id}/retry")
    public Result<Void> manualRetry(@PathVariable Long id) {
        notifyLogService.manualRetry(id);
        return Result.success(null);
    }
}
