package com.traffic.alert.controller;

import com.traffic.alert.common.PageResult;
import com.traffic.alert.common.Result;
import com.traffic.alert.dto.GlobalTrackQuery;
import com.traffic.alert.entity.EventTrackLink;
import com.traffic.alert.entity.GlobalTrack;
import com.traffic.alert.entity.TrackMatchLog;
import com.traffic.alert.entity.TrackPoint;
import com.traffic.alert.service.GlobalTrackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "多摄像头接力跟踪")
@RestController
@RequestMapping("/api/tracks")
@RequiredArgsConstructor
public class GlobalTrackController {

    private final GlobalTrackService globalTrackService;

    @Operation(summary = "分页查询全局轨迹")
    @GetMapping("/page")
    public Result<PageResult<GlobalTrack>> page(GlobalTrackQuery query) {
        return Result.success(globalTrackService.page(query));
    }

    @Operation(summary = "获取轨迹详情")
    @GetMapping("/{id}")
    public Result<GlobalTrack> getById(@PathVariable Long id) {
        return Result.success(globalTrackService.getById(id));
    }

    @Operation(summary = "获取轨迹的所有轨迹点")
    @GetMapping("/{id}/points")
    public Result<List<TrackPoint>> listTrackPoints(@PathVariable Long id) {
        return Result.success(globalTrackService.listTrackPoints(id));
    }

    @Operation(summary = "获取轨迹的关键点(进入/离开/事件点)")
    @GetMapping("/{id}/key-points")
    public Result<List<TrackPoint>> listKeyPoints(@PathVariable Long id) {
        return Result.success(globalTrackService.listKeyPoints(id));
    }

    @Operation(summary = "获取轨迹时间线（按摄像头分段）")
    @GetMapping("/{id}/timeline")
    public Result<Map<String, Object>> getTrackTimeline(@PathVariable Long id) {
        return Result.success(globalTrackService.getTrackTimeline(id));
    }

    @Operation(summary = "获取正在跟踪中的轨迹列表")
    @GetMapping("/active")
    public Result<List<GlobalTrack>> listActiveTracks() {
        return Result.success(globalTrackService.listActiveTracks());
    }

    @Operation(summary = "创建全局轨迹")
    @PostMapping
    public Result<GlobalTrack> createTrack(@RequestBody GlobalTrack track) {
        return Result.success(globalTrackService.createTrack(track));
    }

    @Operation(summary = "更新全局轨迹")
    @PutMapping
    public Result<GlobalTrack> updateTrack(@RequestBody GlobalTrack track) {
        return Result.success(globalTrackService.updateTrack(track));
    }

    @Operation(summary = "添加轨迹点")
    @PostMapping("/point")
    public Result<TrackPoint> addTrackPoint(@RequestBody TrackPoint point) {
        return Result.success(globalTrackService.addTrackPoint(point));
    }

    @Operation(summary = "跨摄像头目标匹配（车牌+ReID联合）")
    @PostMapping("/match")
    public Result<TrackMatchLog> matchAcrossCameras(@RequestBody Map<String, Object> params) {
        String sourcePlate = (String) params.get("sourcePlate");
        String sourceReidFeature = (String) params.get("sourceReidFeature");
        String sourceTargetClass = (String) params.get("sourceTargetClass");
        Long sourceCameraId = params.get("sourceCameraId") != null ? Long.valueOf(params.get("sourceCameraId").toString()) : null;
        Long targetCameraId = params.get("targetCameraId") != null ? Long.valueOf(params.get("targetCameraId").toString()) : null;
        String targetPlate = (String) params.get("targetPlate");
        String targetReidFeature = (String) params.get("targetReidFeature");
        Long targetTrackId = params.get("targetTrackId") != null ? Long.valueOf(params.get("targetTrackId").toString()) : null;
        String targetTrackNo = (String) params.get("targetTrackNo");

        return Result.success(globalTrackService.matchAcrossCameras(
                sourcePlate, sourceReidFeature, sourceTargetClass,
                sourceCameraId, null,
                targetCameraId, targetPlate, targetReidFeature,
                targetTrackId, targetTrackNo
        ));
    }

    @Operation(summary = "查找匹配的已存在轨迹")
    @PostMapping("/find-match")
    public Result<GlobalTrack> findMatchingTrack(@RequestBody Map<String, Object> params) {
        String licensePlate = (String) params.get("licensePlate");
        String reidFeature = (String) params.get("reidFeature");
        String targetClass = (String) params.get("targetClass");
        Long cameraId = params.get("cameraId") != null ? Long.valueOf(params.get("cameraId").toString()) : null;
        Double threshold = params.get("threshold") != null ? Double.valueOf(params.get("threshold").toString()) : 0.82;

        return Result.success(globalTrackService.findMatchingTrack(
                licensePlate, reidFeature, targetClass, cameraId, threshold
        ));
    }

    @Operation(summary = "更新轨迹状态 1-跟踪中 2-已丢失 3-已完成")
    @PostMapping("/{id}/status")
    public Result<Void> updateTrackStatus(@PathVariable Long id, @RequestParam int status) {
        globalTrackService.updateTrackStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "批量添加轨迹点（AI引擎推送）")
    @PostMapping("/points/batch")
    public Result<Integer> batchAddTrackPoints(@RequestBody List<TrackPoint> points) {
        return Result.success(globalTrackService.batchAddTrackPoints(points));
    }

    @Operation(summary = "获取事件关联的轨迹列表")
    @GetMapping("/by-event/{eventId}")
    public Result<List<GlobalTrack>> listByEvent(@PathVariable Long eventId) {
        return Result.success(globalTrackService.listByEvent(eventId));
    }

    @Operation(summary = "获取事件轨迹关联列表")
    @GetMapping("/event-links/{eventId}")
    public Result<List<EventTrackLink>> listEventLinks(@PathVariable Long eventId) {
        return Result.success(globalTrackService.listEventLinks(eventId));
    }

    @Operation(summary = "关联告警事件与轨迹")
    @PostMapping("/link-event")
    public Result<EventTrackLink> linkEventToTrack(@RequestBody Map<String, Object> params) {
        Long eventId = params.get("eventId") != null ? Long.valueOf(params.get("eventId").toString()) : null;
        String eventNo = (String) params.get("eventNo");
        Long trackId = params.get("trackId") != null ? Long.valueOf(params.get("trackId").toString()) : null;
        String trackNo = (String) params.get("trackNo");
        Integer linkType = params.get("linkType") != null ? Integer.valueOf(params.get("linkType").toString()) : null;
        Long cameraId = params.get("cameraId") != null ? Long.valueOf(params.get("cameraId").toString()) : null;
        Long trackPointId = params.get("trackPointId") != null ? Long.valueOf(params.get("trackPointId").toString()) : null;
        String description = (String) params.get("description");

        return Result.success(globalTrackService.linkEventToTrack(
                eventId, eventNo, trackId, trackNo, linkType, null, cameraId, trackPointId, description
        ));
    }
}
