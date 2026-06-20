package com.traffic.alert.config;

import com.traffic.alert.dto.DriverBehaviorAnalysisResult;
import com.traffic.alert.entity.Camera;
import com.traffic.alert.service.CameraService;
import com.traffic.alert.service.DriverBehaviorAnalyzer;
import com.traffic.alert.service.DriverBehaviorService;
import com.traffic.alert.service.VideoRecordingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class DriverBehaviorSchedule {

    private final DriverBehaviorConfig config;
    private final DriverBehaviorAnalyzer analyzer;
    private final DriverBehaviorService driverBehaviorService;
    private final CameraService cameraService;
    private final VideoRecordingService videoRecordingService;

    private final ConcurrentHashMap<Long, BufferedImage> previousFrameMap = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${driver.behavior.detectionIntervalMinutes:5}m", initialDelay = 60000)
    public void scheduledDriverBehaviorDetection() {
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return;
        }

        log.info("========== 开始驾驶员行为定时检测 ==========");
        long startTime = System.currentTimeMillis();

        AtomicInteger totalCount = new AtomicInteger(0);
        AtomicInteger realFrameCount = new AtomicInteger(0);
        AtomicInteger mockCount = new AtomicInteger(0);
        AtomicInteger abnormalCount = new AtomicInteger(0);

        try {
            List<Camera> inCarCameras = driverBehaviorService.listInCarCameras();
            if (inCarCameras.isEmpty()) {
                log.info("未找到车内摄像头，跳过检测");
                return;
            }

            log.info("找到 {} 个车内摄像头，开始逐一路检测", inCarCameras.size());

            for (Camera camera : inCarCameras) {
                try {
                    DriverBehaviorAnalysisResult result = detectSingleCamera(camera);
                    driverBehaviorService.saveDetectionResult(result);

                    totalCount.incrementAndGet();
                    if (Boolean.TRUE.equals(result.getIsRealFrame())) {
                        realFrameCount.incrementAndGet();
                    } else {
                        mockCount.incrementAndGet();
                    }
                    if (Boolean.TRUE.equals(result.getIsAbnormal())) {
                        abnormalCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("检测摄像头[{}]行为异常失败: {}", camera.getId(), e.getMessage(), e);
                }
            }

            long cost = System.currentTimeMillis() - startTime;
            log.info("========== 驾驶员行为定时检测完成 ==========");
            log.info("总计: {} 个摄像头, 真实帧: {} 个, 模拟: {} 个, 异常: {} 个, 耗时: {}ms",
                    totalCount.get(), realFrameCount.get(), mockCount.get(), abnormalCount.get(), cost);

        } catch (Exception e) {
            log.error("驾驶员行为定时检测异常: {}", e.getMessage(), e);
        }
    }

    private DriverBehaviorAnalysisResult detectSingleCamera(Camera camera) {
        Long cameraId = camera.getId();
        String cameraName = camera.getCameraName();
        String streamUrl = camera.getStreamUrl();
        BufferedImage previousFrame = previousFrameMap.get(cameraId);

        if (Boolean.TRUE.equals(config.getEnableRealFrameCapture())
                && streamUrl != null && !streamUrl.trim().isEmpty()
                && videoRecordingService.isFfmpegAvailable()) {

            long captureStart = System.currentTimeMillis();
            BufferedImage currentFrame = videoRecordingService.captureFrame(
                    streamUrl,
                    config.getFrameCaptureTimeoutSeconds(),
                    config.getFrameMaxWidth()
            );
            long captureCost = System.currentTimeMillis() - captureStart;

            if (currentFrame != null) {
                DriverBehaviorAnalysisResult result = analyzer.analyze(
                        cameraId, cameraName, currentFrame, previousFrame);

                if (previousFrame != null) {
                    previousFrame.flush();
                }
                previousFrameMap.put(cameraId, currentFrame);

                result.setCameraCode(camera.getCameraCode());
                result.setRoadName(camera.getRoadName());
                result.setLongitude(camera.getLongitude());
                result.setLatitude(camera.getLatitude());
                result.setIsRealFrame(true);
                result.setFrameCaptureCostMs(captureCost);

                log.debug("摄像头[{}]真实帧检测完成: 异常={}, 得分={}, 抓帧耗时={}ms",
                        cameraId, result.getIsAbnormal(), result.getOverallScore(), captureCost);

                return result;
            } else {
                log.warn("摄像头[{}]抓帧失败，使用模拟数据", cameraId);
            }
        }

        int scenario = decideScenario(camera);
        DriverBehaviorAnalysisResult result = analyzer.analyzeMock(cameraId, cameraName, scenario);
        result.setCameraCode(camera.getCameraCode());
        result.setRoadName(camera.getRoadName());
        result.setLongitude(camera.getLongitude());
        result.setLatitude(camera.getLatitude());
        result.setIsRealFrame(false);
        result.setFrameCaptureCostMs(0L);

        log.debug("摄像头[{}]模拟检测完成: 异常={}, 得分={}", cameraId, result.getIsAbnormal(), result.getOverallScore());

        return result;
    }

    private int decideScenario(Camera camera) {
        long hash = Math.abs(camera.getId() * 31 + (camera.getCameraName() != null ? camera.getCameraName().hashCode() : 0));
        int mode = (int) (hash % 10);

        if (mode < 5) return 0;
        if (mode == 5) return 1;
        if (mode == 6) return 2;
        if (mode == 7) return 3;
        return 4;
    }

    private String maskUrl(String url) {
        if (url == null) return null;
        if (url.contains("@")) {
            int atIndex = url.lastIndexOf("@");
            int slashIndex = url.indexOf("://");
            if (slashIndex >= 0 && atIndex > slashIndex) {
                return url.substring(0, slashIndex + 3) + "***:***@" + url.substring(atIndex + 1);
            }
        }
        return url;
    }
}
