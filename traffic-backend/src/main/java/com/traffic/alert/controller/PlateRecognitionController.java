package com.traffic.alert.controller;

import com.traffic.alert.common.PageResult;
import com.traffic.alert.common.Result;
import com.traffic.alert.dto.PlateRecognitionQuery;
import com.traffic.alert.entity.PlateRecognition;
import com.traffic.alert.service.PlateRecognitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "车牌识别")
@RestController
@RequestMapping("/api/plate-recognitions")
@RequiredArgsConstructor
public class PlateRecognitionController {

    private final PlateRecognitionService service;

    @Operation(summary = "分页查询车牌识别记录")
    @GetMapping
    public Result<PageResult<PlateRecognition>> page(PlateRecognitionQuery query) {
        return Result.success(service.page(query));
    }

    @Operation(summary = "根据ID获取识别记录")
    @GetMapping("/{id}")
    public Result<PlateRecognition> getById(@PathVariable Long id) {
        return Result.success(service.getById(id));
    }

    @Operation(summary = "按事件ID获取识别记录列表")
    @GetMapping("/event/{alertEventId}")
    public Result<List<PlateRecognition>> listByEventId(@PathVariable Long alertEventId) {
        return Result.success(service.listByAlertEventId(alertEventId));
    }

    @Operation(summary = "按事件编号获取识别记录列表")
    @GetMapping("/event-no/{eventNo}")
    public Result<List<PlateRecognition>> listByEventNo(@PathVariable String eventNo) {
        return Result.success(service.listByEventNo(eventNo));
    }

    @Operation(summary = "新增识别记录")
    @PostMapping
    public Result<PlateRecognition> create(@RequestBody PlateRecognition entity) {
        return Result.success(service.save(entity));
    }

    @Operation(summary = "更新识别记录")
    @PutMapping("/{id}")
    public Result<PlateRecognition> update(@PathVariable Long id, @RequestBody PlateRecognition entity) {
        entity.setId(id);
        return Result.success(service.save(entity));
    }

    @Operation(summary = "删除识别记录")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }
}
