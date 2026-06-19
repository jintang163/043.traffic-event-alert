package com.traffic.alert.controller;

import com.traffic.alert.common.Result;
import com.traffic.alert.entity.PoliceSystemConfig;
import com.traffic.alert.service.PoliceSystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "交警系统配置")
@RestController
@RequestMapping("/api/police-system-configs")
@RequiredArgsConstructor
public class PoliceSystemConfigController {

    private final PoliceSystemConfigService service;

    @Operation(summary = "查询全部交警系统配置")
    @GetMapping
    public Result<List<PoliceSystemConfig>> list() {
        return Result.success(service.listAll());
    }

    @Operation(summary = "查询启用的交警系统配置")
    @GetMapping("/enabled")
    public Result<List<PoliceSystemConfig>> listEnabled() {
        return Result.success(service.listEnabled());
    }

    @Operation(summary = "根据ID获取配置")
    @GetMapping("/{id}")
    public Result<PoliceSystemConfig> getById(@PathVariable Long id) {
        return Result.success(service.getById(id));
    }

    @Operation(summary = "根据系统代号获取配置")
    @GetMapping("/code/{systemCode}")
    public Result<PoliceSystemConfig> getByCode(@PathVariable String systemCode) {
        return Result.success(service.getByCode(systemCode));
    }

    @Operation(summary = "新增配置")
    @PostMapping
    public Result<PoliceSystemConfig> create(@RequestBody PoliceSystemConfig entity) {
        return Result.success(service.save(entity));
    }

    @Operation(summary = "更新配置")
    @PutMapping("/{id}")
    public Result<PoliceSystemConfig> update(@PathVariable Long id, @RequestBody PoliceSystemConfig entity) {
        entity.setId(id);
        return Result.success(service.save(entity));
    }

    @Operation(summary = "删除配置")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }
}
