package com.traffic.alert.config;

import com.traffic.alert.dto.VideoQualityAnalysisResult;
import com.traffic.alert.entity.Camera;
import com.traffic.alert.service.CameraService;
import com.traffic.alert.service.VideoQualityAnalyzer;
import com.traffic.alert.service.VideoQualityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoQualitySchedule {

    private final VideoQualityConfig config;
    private final CameraService cameraService;
    private final VideoQualityAnalyzer analyzer;
    private final VideoQualityService videoQualityService;

    private final Random random = new Random();

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

            for (int i = 0; i < cameras.size(); i++) {
                Camera camera = cameras.get(i);
                try {
                    int scenario = decideScenario(camera);
                    VideoQualityAnalysisResult result = analyzer.analyzeMock(
                            camera.getId(), camera.getCameraName(), scenario
                    );
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
            log.info("========== 定时视频质量检测完成: 成功={}, 异常={}, 耗时={}ms ==========",
                    successCount.get(), abnormalCount.get(), cost);

        } catch (Exception e) {
            log.error("定时视频质量检测异常: {}", e.getMessage(), e);
        }
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
