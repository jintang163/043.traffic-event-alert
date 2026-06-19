package com.traffic.alert.controller;

import com.traffic.alert.common.PageResult;
import com.traffic.alert.common.Result;
import com.traffic.alert.dto.NotifyTemplateQuery;
import com.traffic.alert.entity.NotifyTemplate;
import com.traffic.alert.service.NotifyTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "通知模板管理")
@RestController
@RequestMapping("/api/notify/templates")
@RequiredArgsConstructor
public class NotifyTemplateController {

    private final NotifyTemplateService notifyTemplateService;

    @Operation(summary = "分页查询通知模板")
    @GetMapping("/page")
    public Result<PageResult<NotifyTemplate>> page(NotifyTemplateQuery query) {
        return Result.success(notifyTemplateService.page(query));
    }

    @Operation(summary = "查询所有模板")
    @GetMapping("/list")
    public Result<List<NotifyTemplate>> list() {
        return Result.success(notifyTemplateService.listAll());
    }

    @Operation(summary = "获取模板详情")
    @GetMapping("/{id}")
    public Result<NotifyTemplate> getById(@PathVariable Long id) {
        return Result.success(notifyTemplateService.getById(id));
    }

    @Operation(summary = "保存通知模板")
    @PostMapping
    public Result<NotifyTemplate> save(@RequestBody NotifyTemplate template) {
        return Result.success(notifyTemplateService.save(template));
    }

    @Operation(summary = "删除通知模板")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        notifyTemplateService.delete(id);
        return Result.success(null);
    }
}
