package com.traffic.alert.controller;

import com.traffic.alert.common.PageResult;
import com.traffic.alert.common.Result;
import com.traffic.alert.dto.*;
import com.traffic.alert.entity.Camera;
import com.traffic.alert.entity.VideoHealthDiagnosis;
import com.traffic.alert.entity.VideoQualityRecord;
import com.traffic.alert.service.CameraService;
import com.traffic.alert.service.VideoQualityAnalyzer;
import com.traffic.alert.service.VideoQualityService;
import com.traffic.alert.service.VideoRecordingService;
import com.traffic.alert.config.VideoQualityConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Tag(name = "视频质量诊断")
@RestController
@RequestMapping("/api/video-quality")
@RequiredArgsConstructor
public class VideoQualityController {

    private final VideoQualityService videoQualityService;
    private final VideoQualityAnalyzer analyzer;
    private final VideoRecordingService videoRecordingService;
    private final CameraService cameraService;
    private final VideoQualityConfig config;

    private final ConcurrentHashMap<Long, BufferedImage> previousFrameMap = new ConcurrentHashMap<>();

    @Operation(summary = "摄像头健康度看板数据")
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> getHealthDashboard() {
        return Result.success(videoQualityService.getHealthDashboard());
    }

    @Operation(summary = "分页查询视频质量检测记录")
    @GetMapping("/records/page")
    public Result<PageResult<VideoQualityRecord>> pageRecords(VideoQualityRecordQuery query) {
        return Result.success(videoQualityService.pageRecords(query));
    }

    @Operation(summary = "获取视频质量检测记录详情")
    @GetMapping("/records/{id}")
    public Result<VideoQualityRecord> getRecordById(@PathVariable Long id) {
        return Result.success(videoQualityService.getRecordById(id));
    }

    @Operation(summary = "获取摄像头最近检测记录")
    @GetMapping("/records/recent")
    public Result<List<VideoQualityRecord>> getRecentRecords(
            @RequestParam(required = false) Long cameraId,
            @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(videoQualityService.getRecentRecords(cameraId, limit));
    }

    @Operation(summary = "分页查询健康度诊断报告")
    @GetMapping("/diagnosis/page")
    public Result<PageResult<VideoHealthDiagnosis>> pageDiagnosis(VideoHealthDiagnosisQuery query) {
        return Result.success(videoQualityService.pageDiagnosis(query));
    }

    @Operation(summary = "获取健康度诊断详情")
    @GetMapping("/diagnosis/{id}")
    public Result<VideoHealthDiagnosis> getDiagnosisById(@PathVariable Long id) {
        return Result.success(videoQualityService.getDiagnosisById(id));
    }

    @Operation(summary = "生成指定摄像头当日健康度诊断")
    @PostMapping("/diagnosis/generate/{cameraId}")
    public Result<VideoHealthDiagnosis> generateDailyDiagnosis(
            @PathVariable Long cameraId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(videoQualityService.generateDailyDiagnosis(cameraId, date));
    }

    @Operation(summary = "批量生成所有摄像头当日健康度诊断")
    @PostMapping("/diagnosis/generate-all")
    public Result<Void> generateAllDailyDiagnosis(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        videoQualityService.generateAllDailyDiagnosis(date);
        return Result.success();
    }

    @Operation(summary = "生成周健康度诊断")
    @PostMapping("/diagnosis/generate-weekly/{cameraId}")
    public Result<VideoHealthDiagnosis> generateWeeklyDiagnosis(
            @PathVariable Long cameraId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate) {
        return Result.success(videoQualityService.generateWeeklyDiagnosis(cameraId, weekStartDate));
    }

    @Operation(summary = "设备巡检报表")
    @GetMapping("/patrol-report")
    public Result<Map<String, Object>> getPatrolReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "DAILY") String periodType) {
        return Result.success(videoQualityService.getPatrolReport(startDate, endDate, periodType));
    }

    @Operation(summary = "手动触发单个摄像头检测(优先真实帧,失败回退模拟)")
    @PostMapping("/detect/{cameraId}")
    public Result<VideoQualityAnalysisResult> manualDetect(
            @PathVariable Long cameraId,
            @RequestParam(defaultValue = "false") Boolean forceMock,
            @RequestParam(defaultValue = "5") Integer scenario) {

        Camera camera = cameraService.getById(cameraId);
        if (camera == null) {
            return Result.error("摄像头不存在");
        }

        VideoQualityAnalysisResult result = null;
        BufferedImage previousFrame = previousFrameMap.get(cameraId);

        if (!Boolean.TRUE.equals(forceMock)
                && Boolean.TRUE.equals(config.getEnableRealFrameCapture())
                && camera.getStreamUrl() != null && !camera.getStreamUrl().trim().isEmpty()
                && videoRecordingService.isFfmpegAvailable()) {

            long captureStart = System.currentTimeMillis();
            BufferedImage currentFrame = videoRecordingService.captureFrame(camera.getStreamUrl());
            long captureCost = System.currentTimeMillis() - captureStart;

            if (currentFrame != null) {
                log.info("手动检测-真实帧抓帧成功: cameraId={}, {}x{}, 耗时={}ms",
                        cameraId, currentFrame.getWidth(), currentFrame.getHeight(), captureCost);

                result = analyzer.analyze(cameraId, camera.getCameraName(), currentFrame, previousFrame);
                result.setIsRealFrame(true);
                result.setFrameCaptureCostMs(captureCost);

                if (previousFrame != null) {
                    previousFrame.flush();
                }
                previousFrameMap.put(cameraId, currentFrame);
            } else {
                log.warn("手动检测-真实帧抓帧失败，将使用模拟数据: cameraId={}", cameraId);
            }
        }

        if (result == null) {
            result = analyzer.analyzeMock(cameraId, camera.getCameraName(), scenario);
            result.setIsRealFrame(false);
            result.setFrameCaptureCostMs(0L);
        }

        videoQualityService.saveDetectionResult(result);
        return Result.success(result);
    }

    @Operation(summary = "批量手动触发检测(模拟)")
    @PostMapping("/detect/batch-mock")
    public Result<Map<String, Object>> batchMockDetect(@RequestBody VideoQualityManualDetectRequest request) {
        int scenario = request.getFrameCount() != null ? request.getFrameCount() : 5;
        Long cameraId = request.getCameraId();
        if (cameraId != null) {
            Camera camera = cameraService.getById(cameraId);
            if (camera == null) {
                return Result.error("摄像头不存在");
            }
            VideoQualityAnalysisResult result = analyzer.analyzeMock(cameraId, camera.getCameraName(), scenario);
            result.setIsRealFrame(false);
            result.setFrameCaptureCostMs(0L);
            videoQualityService.saveDetectionResult(result);
            return Result.success(Map.of(
                    "total", 1,
                    "results", List.of(result)
            ));
        }
        return Result.error("请指定cameraId参数");
    }

    @Operation(summary = "FFmpeg可用性和抓帧测试")
    @GetMapping("/ffmpeg-status")
    public Result<Map<String, Object>> getFfmpegStatus() {
        boolean available = videoRecordingService.isFfmpegAvailable();
        return Result.success(Map.of(
                "ffmpegAvailable", available,
                "realFrameCaptureEnabled", Boolean.TRUE.equals(config.getEnableRealFrameCapture())
        ));
    }
}
