package com.traffic.alert.controller;

import com.traffic.alert.common.PageResult;
import com.traffic.alert.common.Result;
import com.traffic.alert.dto.NotifyChannelQuery;
import com.traffic.alert.entity.NotifyChannel;
import com.traffic.alert.service.NotifyChannelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "通知渠道管理")
@RestController
@RequestMapping("/api/notify/channels")
@RequiredArgsConstructor
public class NotifyChannelController {

    private final NotifyChannelService notifyChannelService;

    @Operation(summary = "分页查询通知渠道")
    @GetMapping("/page")
    public Result<PageResult<NotifyChannel>> page(NotifyChannelQuery query) {
        return Result.success(notifyChannelService.page(query));
    }

    @Operation(summary = "查询所有渠道")
    @GetMapping("/list")
    public Result<List<NotifyChannel>> list() {
        return Result.success(notifyChannelService.listAll());
    }

    @Operation(summary = "查询已启用渠道")
    @GetMapping("/enabled")
    public Result<List<NotifyChannel>> listEnabled() {
        return Result.success(notifyChannelService.listEnabled());
    }

    @Operation(summary = "获取渠道详情")
    @GetMapping("/{id}")
    public Result<NotifyChannel> getById(@PathVariable Long id) {
        return Result.success(notifyChannelService.getById(id));
    }

    @Operation(summary = "保存渠道配置")
    @PostMapping
    public Result<NotifyChannel> save(@RequestBody NotifyChannel channel) {
        return Result.success(notifyChannelService.save(channel));
    }

    @Operation(summary = "删除渠道")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        notifyChannelService.delete(id);
        return Result.success(null);
    }
}
