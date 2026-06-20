package com.traffic.alert.controller;

import com.traffic.alert.common.PageResult;
import com.traffic.alert.common.Result;
import com.traffic.alert.dto.CameraQuery;
import com.traffic.alert.dto.PtzControlRequest;
import com.traffic.alert.entity.Camera;
import com.traffic.alert.service.CameraService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "摄像头管理")
@RestController
@RequestMapping("/api/cameras")
@RequiredArgsConstructor
public class CameraController {

    private final CameraService cameraService;

    @Operation(summary = "分页查询摄像头")
    @GetMapping("/page")
    public Result<PageResult<Camera>> page(CameraQuery query) {
        return Result.success(cameraService.page(query));
    }

    @Operation(summary = "获取摄像头列表")
    @GetMapping("/list")
    public Result<List<Camera>> list() {
        return Result.success(cameraService.list());
    }

    @Operation(summary = "获取摄像头详情")
    @GetMapping("/{id}")
    public Result<Camera> getById(@PathVariable Long id) {
        return Result.success(cameraService.getById(id));
    }

    @Operation(summary = "保存摄像头")
    @PostMapping
    public Result<Camera> save(@RequestBody Camera camera) {
        return Result.success(cameraService.save(camera));
    }

    @Operation(summary = "删除摄像头")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        cameraService.delete(id);
        return Result.success();
    }

    @Operation(summary = "获取摄像头流地址")
    @GetMapping("/{id}/stream")
    public Result<Map<String, String>> getStreamUrl(@PathVariable Long id) {
        String url = cameraService.getStreamUrl(id);
        return Result.success(Map.of("streamUrl", url));
    }

    @Operation(summary = "云台控制")
    @PostMapping("/{id}/ptz")
    public Result<Map<String, Object>> ptzControl(@PathVariable Long id, @RequestBody PtzControlRequest request) {
        return Result.success(cameraService.ptzControl(id, request));
    }

    @Operation(summary = "获取摄像头统计信息")
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics() {
        return Result.success(cameraService.getStatistics());
    }

    @Operation(summary = "获取摄像头行车道区域")
    @GetMapping("/{id}/road-region")
    public Result<Object> getRoadRegion(@PathVariable Long id) {
        return Result.success(cameraService.getRoadRegion(id));
    }

    @Operation(summary = "设置摄像头行车道区域")
    @PutMapping("/{id}/road-region")
    public Result<Boolean> setRoadRegion(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        String roadRegionPixel = (String) params.get("roadRegionPixel");
        return Result.success(cameraService.setRoadRegion(id, roadRegionPixel));
    }

    @Operation(summary = "同步行车道区域到AI引擎")
    @PostMapping("/{id}/road-region/sync")
    public Result<Boolean> syncRoadRegion(@PathVariable Long id) {
        return Result.success(cameraService.syncRoadRegionToEngine(id));
    }

    @Operation(summary = "获取摄像头LED情报板配置")
    @GetMapping("/{id}/led-config")
    public Result<Map<String, Object>> getLedConfig(@PathVariable Long id) {
        return Result.success(cameraService.getLedConfig(id));
    }

    @Operation(summary = "设置摄像头LED情报板配置")
    @PutMapping("/{id}/led-config")
    public Result<Boolean> setLedConfig(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        String ledConfig = (String) params.get("ledConfig");
        return Result.success(cameraService.setLedConfig(id, ledConfig));
    }
}
