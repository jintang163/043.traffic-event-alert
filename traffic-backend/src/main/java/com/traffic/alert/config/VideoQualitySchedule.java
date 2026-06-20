package com.traffic.alert.config;

import com.traffic.alert.dto.VideoQualityAnalysisResult;
import com.traffic.alert.entity.Camera;
import com.traffic.alert.service.CameraService;
import com.traffic.alert.service.VideoQualityAnalyzer;
import com.traffic.alert.service.VideoQualityService;
import com.traffic.alert.service.VideoRecordingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoQualitySchedule {

    private final VideoQualityConfig config;
    private final CameraService cameraService;
    private final VideoQualityAnalyzer analyzer;
    private final VideoQualityService videoQualityService;
    private final VideoRecordingService videoRecordingService;

    private final Random random = new Random();
    private final Map<Long, BufferedImage> previousFrameMap = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${video.quality.detectionIntervalMinutes:15} * 60 * 1000")
    public void scheduledQualityDetection() {
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return;
        }
        long start = System.currentTimeMillis();
        log.info("========== 定时视频质量检测开始 ==========");

        try {
            List<Camera> cameras = cameraService.list();
            if (cameras.isEmpty()) {
                log.info("无可用摄像头，跳过本次检测");
                return;
            }

            int limit = Math.min(cameras.size(), config.getConcurrentDetectionLimit());
            log.info("待检测摄像头数量: {}, 并发限制: {}", cameras.size(), limit);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger abnormalCount = new AtomicInteger(0);
            AtomicInteger realFrameCount = new AtomicInteger(0);
            AtomicInteger mockFallbackCount = new AtomicInteger(0);

            for (int i = 0; i < cameras.size(); i++) {
                Camera camera = cameras.get(i);
                try {
                    VideoQualityAnalysisResult result = detectSingleCamera(camera);

                    if (result == null) {
                        log.warn("检测结果为空: cameraId={}, cameraName={}", camera.getId(), camera.getCameraName());
                        continue;
                    }

                    if (Boolean.TRUE.equals(result.getIsRealFrame())) {
                        realFrameCount.incrementAndGet();
                    } else {
                        mockFallbackCount.incrementAndGet();
                    }

                    videoQualityService.saveDetectionResult(result);
                    successCount.incrementAndGet();
                    if (Boolean.TRUE.equals(result.getIsAbnormal())) {
                        abnormalCount.incrementAndGet();
                    }
                    if ((i + 1) % 10 == 0) {
                        log.info("检测进度: {}/{}", (i + 1), cameras.size());
                    }
                } catch (Exception e) {
                    log.error("检测摄像头失败: cameraId={}, cameraName={}, err={}",
                            camera.getId(), camera.getCameraName(), e.getMessage());
                }
            }

            long cost = System.currentTimeMillis() - start;
            log.info("========== 定时视频质量检测完成: 成功={}, 异常={}, 真实帧={}, 模拟={}, 耗时={}ms ==========",
                    successCount.get(), abnormalCount.get(), realFrameCount.get(), mockFallbackCount.get(), cost);

        } catch (Exception e) {
            log.error("定时视频质量检测异常: {}", e.getMessage(), e);
        }
    }

    private VideoQualityAnalysisResult detectSingleCamera(Camera camera) {
        Long cameraId = camera.getId();
        String cameraName = camera.getCameraName();
        String streamUrl = camera.getStreamUrl();

        BufferedImage previousFrame = previousFrameMap.get(cameraId);

        if (Boolean.TRUE.equals(config.getEnableRealFrameCapture())
                && streamUrl != null && !streamUrl.trim().isEmpty()
                && videoRecordingService.isFfmpegAvailable()) {

            long captureStart = System.currentTimeMillis();
            BufferedImage currentFrame = videoRecordingService.captureFrame(streamUrl);
            long captureCost = System.currentTimeMillis() - captureStart;

            if (currentFrame != null) {
                log.debug("摄像头[{}]抓帧成功: {}x{}, 耗时={}ms", cameraId,
                        currentFrame.getWidth(), currentFrame.getHeight(), captureCost);

                VideoQualityAnalysisResult result = analyzer.analyze(
                        cameraId, cameraName, currentFrame, previousFrame);

                if (previousFrame != null) {
                    previousFrame.flush();
                }
                previousFrameMap.put(cameraId, currentFrame);

                result.setIsRealFrame(true);
                result.setFrameCaptureCostMs(captureCost);
                return result;
            } else {
                log.warn("摄像头[{}]抓帧失败，将使用模拟数据作为兜底，streamUrl={}",
                        cameraId, maskUrl(streamUrl));
            }
        }

        int scenario = decideScenario(camera);
        VideoQualityAnalysisResult result = analyzer.analyzeMock(cameraId, cameraName, scenario);
        result.setIsRealFrame(false);
        result.setFrameCaptureCostMs(0L);
        return result;
    }

    private String maskUrl(String url) {
        if (url == null) return null;
        int atIndex = url.indexOf('@');
        if (atIndex > 0) {
            int protocolEnd = url.indexOf("://") + 3;
            return url.substring(0, protocolEnd) + "***:***@" + url.substring(atIndex + 1);
        }
        return url;
    }

    private int decideScenario(Camera camera) {
        Long id = camera.getId();
        Integer status = camera.getStatus();
        Integer online = camera.getOnlineStatus();

        if (id == 4 || (online != null && online == 0)) {
            return 1;
        }
        if (id == 3) {
            int[] options = {2, 3, 2, 5};
            return options[random.nextInt(options.length)];
        }
        if (id == 2) {
            return random.nextInt(3) == 0 ? 4 : 0;
        }
        int r = random.nextInt(100);
        if (r < 2) return 1;
        if (r < 5) return 4;
        if (r < 10) return 2;
        if (r < 15) return 3;
        return 0;
    }

    @Scheduled(cron = "0 5 0 * * ?")
    public void generateDailyDiagnosisReport() {
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return;
        }
        log.info("========== 开始生成视频质量日诊断报告 ==========");
        long start = System.currentTimeMillis();
        try {
            videoQualityService.generateAllDailyDiagnosis(LocalDate.now().minusDays(1));
            log.info("========== 日诊断报告生成完成, 耗时={}ms ==========", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("生成日诊断报告异常: {}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "0 10 0 ? * MON")
    public void generateWeeklyDiagnosisReport() {
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return;
        }
        log.info("========== 开始生成视频质量周诊断报告 ==========");
        long start = System.currentTimeMillis();
        try {
            LocalDate lastWeekStart = LocalDate.now()
                    .with(TemporalAdjusters.previous(java.time.DayOfWeek.MONDAY))
                    .minusWeeks(1);
            List<Camera> cameras = cameraService.list();
            for (Camera camera : cameras) {
                try {
                    videoQualityService.generateWeeklyDiagnosis(camera.getId(), lastWeekStart);
                } catch (Exception e) {
                    log.error("生成周诊断失败: cameraId={}, err={}", camera.getId(), e.getMessage());
                }
            }
            log.info("========== 周诊断报告生成完成, 共{}台, 耗时={}ms ==========",
                    cameras.size(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("生成周诊断报告异常: {}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "0 0 0 1 * ?")
    public void generateMonthlyDiagnosisSummary() {
        log.info("月度视频质量诊断汇总任务执行: {}", LocalDateTime.now());
    }
}
