package com.traffic.alert.controller;

import com.traffic.alert.common.PageResult;
import com.traffic.alert.common.Result;
import com.traffic.alert.dto.GeoFenceQuery;
import com.traffic.alert.entity.GeoFence;
import com.traffic.alert.service.GeoFenceService;
import com.traffic.alert.utils.GeoPolygonUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "电子围栏管理")
@RestController
@RequestMapping("/api/geo-fences")
@RequiredArgsConstructor
public class GeoFenceController {

    private final GeoFenceService geoFenceService;

    @Operation(summary = "分页查询电子围栏")
    @GetMapping("/page")
    public Result<PageResult<GeoFence>> page(GeoFenceQuery query) {
        return Result.success(geoFenceService.page(query));
    }

    @Operation(summary = "获取围栏详情")
    @GetMapping("/{id}")
    public Result<GeoFence> getById(@PathVariable Long id) {
        return Result.success(geoFenceService.getById(id));
    }

    @Operation(summary = "按摄像头查询围栏列表")
    @GetMapping("/camera/{cameraId}")
    public Result<List<GeoFence>> listByCamera(@PathVariable Long cameraId) {
        return Result.success(geoFenceService.listByCamera(cameraId));
    }

    @Operation(summary = "获取所有启用的围栏")
    @GetMapping("/enabled")
    public Result<List<GeoFence>> listAllEnabled() {
        return Result.success(geoFenceService.listAllEnabled());
    }

    @Operation(summary = "新增/更新电子围栏")
    @PostMapping
    public Result<GeoFence> save(@RequestBody GeoFence geoFence) {
        return Result.success(geoFenceService.save(geoFence));
    }

    @Operation(summary = "删除电子围栏")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        geoFenceService.delete(id);
        return Result.success();
    }

    @Operation(summary = "启用/禁用告警")
    @PostMapping("/{id}/alert")
    public Result<GeoFence> toggleAlert(@PathVariable Long id, @RequestParam boolean enabled) {
        return Result.success(geoFenceService.toggleAlert(id, enabled));
    }

    @Operation(summary = "启用/禁用围栏")
    @PostMapping("/{id}/status")
    public Result<GeoFence> toggleStatus(@PathVariable Long id, @RequestParam int status) {
        return Result.success(geoFenceService.toggleStatus(id, status));
    }

    @Operation(summary = "检测点是否在围栏内")
    @GetMapping("/{id}/check-point")
    public Result<Boolean> checkPointInFence(
            @PathVariable Long id,
            @RequestParam double lng,
            @RequestParam double lat) {
        return Result.success(geoFenceService.checkPointInFence(id, lng, lat));
    }

    @Operation(summary = "查询包含某点的所有围栏")
    @GetMapping("/containing-point")
    public Result<List<GeoFence>> findFencesContainingPoint(
            @RequestParam double lng,
            @RequestParam double lat,
            @RequestParam(required = false) Long cameraId) {
        return Result.success(geoFenceService.findFencesContainingPoint(lng, lat, cameraId));
    }

    @Operation(summary = "计算多边形面积")
    @PostMapping("/calculate-area")
    public Result<Map<String, Object>> calculateArea(@RequestBody Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<List<Double>> points = (List<List<Double>>) params.get("points");

        List<GeoPolygonUtils.Point> polygon = new java.util.ArrayList<>();
        if (points != null) {
            for (List<Double> p : points) {
                if (p.size() >= 2) {
                    polygon.add(new GeoPolygonUtils.Point(p.get(0), p.get(1)));
                }
            }
        }

        double area = GeoPolygonUtils.calculatePolygonAreaSquareMeters(polygon);
        GeoPolygonUtils.Point center = GeoPolygonUtils.getPolygonCenter(polygon);

        return Result.success(Map.of(
                "area", area,
                "centerLng", center.lng,
                "centerLat", center.lat,
                "pointCount", polygon.size()
        ));
    }
}
