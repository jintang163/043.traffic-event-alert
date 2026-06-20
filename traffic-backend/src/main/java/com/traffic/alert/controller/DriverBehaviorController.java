package com.traffic.alert.controller;

import com.traffic.alert.common.PageResult;
import com.traffic.alert.common.Result;
import com.traffic.alert.config.DriverBehaviorConfig;
import com.traffic.alert.dto.*;
import com.traffic.alert.entity.Camera;
import com.traffic.alert.entity.DriverBehaviorRecord;
import com.traffic.alert.service.CameraService;
import com.traffic.alert.service.DriverBehaviorAnalyzer;
import com.traffic.alert.service.DriverBehaviorService;
import com.traffic.alert.service.VideoRecordingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Tag(name = "驾驶员行为分析")
@RestController
@RequestMapping("/api/driver-behavior")
@RequiredArgsConstructor
public class DriverBehaviorController {

    private final DriverBehaviorService driverBehaviorService;
    private final DriverBehaviorAnalyzer analyzer;
    private final VideoRecordingService videoRecordingService;
    private final CameraService cameraService;
    private final DriverBehaviorConfig config;

    private final ConcurrentHashMap<Long, BufferedImage> previousFrameMap = new ConcurrentHashMap<>();

    @Operation(summary = "获取看板数据")
    @GetMapping("/dashboard")
    public Result<DriverBehaviorDashboard> getDashboard() {
        return Result.success(driverBehaviorService.getDashboard());
    }

    @Operation(summary = "分页查询检测记录")
    @GetMapping("/records/page")
    public Result<PageResult<DriverBehaviorRecord>> pageRecords(DriverBehaviorRecordQuery query) {
        return Result.success(driverBehaviorService.page(query));
    }

    @Operation(summary = "获取检测记录详情")
    @GetMapping("/records/{id}")
    public Result<DriverBehaviorRecord> getRecord(@PathVariable Long id) {
        return Result.success(driverBehaviorService.getById(id));
    }

    @Operation(summary = "获取最近检测记录")
    @GetMapping("/records/recent")
    public Result<List<DriverBehaviorRecord>> getRecentRecords(
            @RequestParam(required = false) Long cameraId,
            @RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(driverBehaviorService.getRecentRecords(cameraId, limit));
    }

    @Operation(summary = "手动检测")
    @PostMapping("/detect/{cameraId}")
    public Result<DriverBehaviorAnalysisResult> manualDetect(
            @PathVariable Long cameraId,
            @RequestParam(defaultValue = "false") Boolean forceMock,
            @RequestParam(defaultValue = "0") Integer scenario) {

        Camera camera = cameraService.getById(cameraId);
        if (camera == null) {
            return Result.error("摄像头不存在");
        }

        if (camera.getCameraType() != null && camera.getCameraType() != 2) {
            log.warn("摄像头[{}]不是车内摄像头，跳过驾驶员行为检测", cameraId);
        }

        DriverBehaviorAnalysisResult result = null;
        BufferedImage previousFrame = previousFrameMap.get(cameraId);

        if (!Boolean.TRUE.equals(forceMock)
                && Boolean.TRUE.equals(config.getEnableRealFrameCapture())
                && camera.getStreamUrl() != null && !camera.getStreamUrl().trim().isEmpty()
                && videoRecordingService.isFfmpegAvailable()) {

            log.info("开始抓取摄像头[{}]真实视频帧进行驾驶员行为分析", cameraId);
            long captureStart = System.currentTimeMillis();
            BufferedImage currentFrame = videoRecordingService.captureFrame(
                    camera.getStreamUrl(),
                    config.getFrameCaptureTimeoutSeconds(),
                    config.getFrameMaxWidth()
            );
            long captureCost = System.currentTimeMillis() - captureStart;

            if (currentFrame != null) {
                result = analyzer.analyze(cameraId, camera.getCameraName(), currentFrame, previousFrame);
                result.setCameraCode(camera.getCameraCode());
                result.setRoadName(camera.getRoadName());
                result.setLongitude(camera.getLongitude());
                result.setLatitude(camera.getLatitude());
                result.setIsRealFrame(true);
                result.setFrameCaptureCostMs(captureCost);

                if (previousFrame != null) {
                    previousFrame.flush();
                }
                previousFrameMap.put(cameraId, currentFrame);

                log.info("摄像头[{}]真实帧分析完成: 异常={}, 得分={}, 抓帧耗时={}ms, 检测耗时={}ms",
                        cameraId, result.getIsAbnormal(), result.getOverallScore(),
                        captureCost, result.getDetectionDurationMs());
            } else {
                log.warn("摄像头[{}]抓帧失败，使用模拟数据", cameraId);
            }
        }

        if (result == null) {
            log.info("使用模拟数据进行驾驶员行为分析，场景={}", scenario);
            result = analyzer.analyzeMock(cameraId, camera.getCameraName(), scenario);
            result.setCameraCode(camera.getCameraCode());
            result.setRoadName(camera.getRoadName());
            result.setLongitude(camera.getLongitude());
            result.setLatitude(camera.getLatitude());
            result.setIsRealFrame(false);
            result.setFrameCaptureCostMs(0L);
        }

        driverBehaviorService.saveDetectionResult(result);

        return Result.success(result);
    }

    @Operation(summary = "批量模拟检测")
    @PostMapping("/detect/batch-mock")
    public Result<Map<String, Object>> batchMockDetect(@RequestBody DriverBehaviorManualDetectRequest request) {
        List<Camera> cameras = driverBehaviorService.listInCarCameras();
        Map<String, Object> result = new HashMap<>();
        int successCount = 0;
        int abnormalCount = 0;

        for (Camera camera : cameras) {
            try {
                DriverBehaviorAnalysisResult analysisResult = analyzer.analyzeMock(
                        camera.getId(), camera.getCameraName(),
                        request.getMockScenario() != null ? request.getMockScenario() : 0
                );
                analysisResult.setCameraCode(camera.getCameraCode());
                analysisResult.setRoadName(camera.getRoadName());
                analysisResult.setLongitude(camera.getLongitude());
                analysisResult.setLatitude(camera.getLatitude());
                analysisResult.setIsRealFrame(false);

                driverBehaviorService.saveDetectionResult(analysisResult);
                successCount++;
                if (Boolean.TRUE.equals(analysisResult.getIsAbnormal())) {
                    abnormalCount++;
                }
            } catch (Exception e) {
                log.error("模拟检测摄像头[{}]失败: {}", camera.getId(), e.getMessage());
            }
        }

        result.put("total", cameras.size());
        result.put("success", successCount);
        result.put("abnormal", abnormalCount);
        return Result.success(result);
    }

    @Operation(summary = "获取FFmpeg状态")
    @GetMapping("/ffmpeg/status")
    public Result<Map<String, Object>> getFfmpegStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("ffmpegAvailable", videoRecordingService.isFfmpegAvailable());
        status.put("enableRealFrameCapture", config.getEnableRealFrameCapture());
        status.put("ffmpegPath", videoRecordingService.getFfmpegPath());
        status.put("detectionIntervalMinutes", config.getDetectionIntervalMinutes());
        return Result.success(status);
    }

    @Operation(summary = "清除帧历史缓存")
    @PostMapping("/clear-history/{cameraId}")
    public Result<Boolean> clearFrameHistory(@PathVariable Long cameraId) {
        analyzer.clearFrameHistory(cameraId);
        previousFrameMap.remove(cameraId);
        return Result.success(true);
    }

    @Operation(summary = "清除所有帧历史缓存")
    @PostMapping("/clear-history-all")
    public Result<Boolean> clearAllFrameHistory() {
        analyzer.clearAllFrameHistory();
        previousFrameMap.clear();
        return Result.success(true);
    }

    @Operation(summary = "获取车内摄像头列表")
    @GetMapping("/cameras")
    public Result<List<Camera>> getInCarCameras() {
        return Result.success(driverBehaviorService.listInCarCameras());
    }
}
