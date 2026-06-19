package com.traffic.alert.controller;

import com.traffic.alert.common.PageResult;
import com.traffic.alert.common.Result;
import com.traffic.alert.dto.WeatherDataQuery;
import com.traffic.alert.entity.WeatherData;
import com.traffic.alert.service.WeatherDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "天气数据管理")
@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherDataController {

    private final WeatherDataService weatherDataService;

    @Operation(summary = "分页查询天气数据")
    @GetMapping("/page")
    public Result<PageResult<WeatherData>> page(WeatherDataQuery query) {
        return Result.success(weatherDataService.page(query));
    }

    @Operation(summary = "获取所有区域编码")
    @GetMapping("/locations")
    public Result<List<String>> getLocationCodes() {
        return Result.success(weatherDataService.getAllLocationCodes());
    }

    @Operation(summary = "获取最新天气数据")
    @GetMapping("/latest")
    public Result<WeatherData> getLatest(@RequestParam(required = false) String locationCode) {
        return Result.success(weatherDataService.getLatest(locationCode));
    }

    @Operation(summary = "获取所有区域最新天气数据")
    @GetMapping("/latest/all")
    public Result<List<WeatherData>> getLatestAll() {
        return Result.success(weatherDataService.getLatestAll());
    }

    @Operation(summary = "获取摄像头对应区域的最新天气数据")
    @GetMapping("/camera/{cameraId}/latest")
    public Result<Map<String, Object>> getLatestForCamera(@PathVariable Long cameraId) {
        return Result.success(weatherDataService.getLatestForCamera(cameraId));
    }

    @Operation(summary = "按时间范围查询天气数据")
    @GetMapping("/range")
    public Result<List<WeatherData>> getByTimeRange(
            @RequestParam(required = false) String locationCode,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(weatherDataService.getByLocationAndTimeRange(locationCode, startTime, endTime));
    }

    @Operation(summary = "获取天气数据详情")
    @GetMapping("/{id}")
    public Result<WeatherData> getById(@PathVariable Long id) {
        return Result.success(weatherDataService.getById(id));
    }

    @Operation(summary = "保存天气数据")
    @PostMapping
    public Result<WeatherData> save(@RequestBody WeatherData data) {
        return Result.success(weatherDataService.save(data));
    }

    @Operation(summary = "删除天气数据")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        weatherDataService.delete(id);
        return Result.success();
    }
}
