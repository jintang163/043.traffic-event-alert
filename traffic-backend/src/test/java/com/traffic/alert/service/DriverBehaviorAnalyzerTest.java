package com.traffic.alert.service;

import com.traffic.alert.config.DriverBehaviorConfig;
import com.traffic.alert.dto.DriverBehaviorAnalysisResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DriverBehaviorAnalyzerTest {

    private DriverBehaviorAnalyzer analyzer;
    private DriverBehaviorConfig config;

    @BeforeEach
    void setUp() {
        config = new DriverBehaviorConfig();

        DriverBehaviorConfig.Thresholds thresholds = new DriverBehaviorConfig.Thresholds();
        thresholds.setPhoneCallConfidence(new BigDecimal("70.00"));
        thresholds.setYawningConfidence(new BigDecimal("70.00"));
        thresholds.setMouthOpenRatio(new BigDecimal("0.45"));
        thresholds.setFatigueConfidence(new BigDecimal("65.00"));
        thresholds.setEyeAspectRatio(new BigDecimal("0.20"));
        thresholds.setPerclosScore(new BigDecimal("50.00"));
        thresholds.setDistractionConfidence(new BigDecimal("65.00"));
        thresholds.setHeadPoseYawThreshold(new BigDecimal("25.00"));
        thresholds.setHeadPosePitchThreshold(new BigDecimal("15.00"));
        thresholds.setConsecutiveAbnormalThreshold(3);
        thresholds.setAlertCooldownSeconds(180);
        config.setThresholds(thresholds);

        DriverBehaviorConfig.Scoring scoring = new DriverBehaviorConfig.Scoring();
        scoring.setPhoneCallWeight(new BigDecimal("30"));
        scoring.setYawningWeight(new BigDecimal("25"));
        scoring.setFatigueWeight(new BigDecimal("25"));
        scoring.setDistractionWeight(new BigDecimal("20"));
        config.setScoring(scoring);

        DriverBehaviorConfig.AlertRules alertRules = new DriverBehaviorConfig.AlertRules();
        alertRules.setPhoneCallAlert(true);
        alertRules.setYawningAlert(true);
        alertRules.setFatigueAlert(true);
        alertRules.setDistractionAlert(true);
        alertRules.setPhoneCallLevel(3);
        alertRules.setYawningLevel(2);
        alertRules.setFatigueLevel(4);
        alertRules.setDistractionLevel(2);
        alertRules.setLedReminderEnabled(true);
        alertRules.setLedDisplaySeconds(30);
        config.setAlertRules(alertRules);

        analyzer = new DriverBehaviorAnalyzer(config);
        analyzer.clearAllFrameHistory();
    }

    private BufferedImage loadImage(String filename) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("driver-behavior-samples/" + filename)) {
            assertNotNull(is, "无法加载测试图像: " + filename);
            return ImageIO.read(is);
        }
    }

    @Test
    @DisplayName("真实图像测试-正常驾驶场景")
    void testAnalyze_NormalDriving() throws IOException {
        BufferedImage frame = loadImage("01-normal-driver.png");
        assertNotNull(frame, "正常驾驶图像应加载成功");
        assertEquals(640, frame.getWidth(), "图像宽度应为640");
        assertEquals(480, frame.getHeight(), "图像高度应为480");

        Long cameraId = 1L;
        String cameraName = "正常驾驶摄像头";
        analyzer.clearFrameHistory(cameraId);

        DriverBehaviorAnalysisResult result = analyzer.analyze(cameraId, cameraName, frame, null);

        System.out.println("=== 正常驾驶场景分析结果 ===");
        System.out.println("摄像头ID: " + result.getCameraId());
        System.out.println("摄像头名称: " + result.getCameraName());
        System.out.println("打电话置信度: " + result.getPhoneCallConfidence() + "%");
        System.out.println("是否打电话: " + result.getIsPhoneCall());
        System.out.println("打哈欠置信度: " + result.getYawningConfidence() + "%");
        System.out.println("嘴巴张开率: " + result.getMouthOpenRatio());
        System.out.println("是否打哈欠: " + result.getIsYawning());
        System.out.println("疲劳置信度: " + result.getFatigueConfidence() + "%");
        System.out.println("眼睛纵横比: " + result.getEyeAspectRatio());
        System.out.println("PERCLOS: " + result.getPerclosScore() + "%");
        System.out.println("是否疲劳: " + result.getIsFatigued());
        System.out.println("分心置信度: " + result.getDistractionConfidence() + "%");
        System.out.println("头部偏航: " + result.getHeadPoseYaw() + "°");
        System.out.println("头部俯仰: " + result.getHeadPosePitch() + "°");
        System.out.println("是否分心: " + result.getIsDistracted());
        System.out.println("综合评分: " + result.getOverallScore());
        System.out.println("行为等级: " + result.getBehaviorLevel());
        System.out.println("是否异常: " + result.getIsAbnormal());
        System.out.println("异常类型: " + result.getAbnormalTypes());
        System.out.println("描述: " + result.getDescription());
        System.out.println("算法版本: " + result.getAlgorithmVersion());
        System.out.println("检测耗时: " + result.getDetectionDurationMs() + "ms");

        assertNotNull(result, "分析结果不应为空");
        assertEquals(cameraId, result.getCameraId(), "摄像头ID应匹配");
        assertEquals(cameraName, result.getCameraName(), "摄像头名称应匹配");
        assertNotNull(result.getDetectionTime(), "检测时间不应为空");
        assertTrue(result.getDetectionTime().isBefore(LocalDateTime.now().plusSeconds(1)), "检测时间应合理");
        assertEquals("v1.0.0-java-pure", result.getAlgorithmVersion(), "算法版本应正确");

        assertNotNull(result.getIsPhoneCall(), "打电话标记不应为空");
        assertFalse(Boolean.TRUE.equals(result.getIsPhoneCall()), "正常驾驶不应检测到打电话");
        assertNotNull(result.getPhoneCallConfidence(), "打电话置信度不应为空");
        assertTrue(result.getPhoneCallConfidence().compareTo(BigDecimal.ZERO) >= 0, "打电话置信度应>=0");
        assertTrue(result.getPhoneCallConfidence().compareTo(BigDecimal.valueOf(70)) < 0,
                "正常驾驶打电话置信度应<70，实际: " + result.getPhoneCallConfidence());

        assertNotNull(result.getIsYawning(), "打哈欠标记不应为空");
        assertFalse(Boolean.TRUE.equals(result.getIsYawning()), "正常驾驶不应检测到打哈欠");
        assertNotNull(result.getYawningConfidence(), "打哈欠置信度不应为空");
        assertTrue(result.getYawningConfidence().compareTo(BigDecimal.ZERO) >= 0, "打哈欠置信度应>=0");
        assertNotNull(result.getMouthOpenRatio(), "嘴巴张开率不应为空");
        assertTrue(result.getMouthOpenRatio().compareTo(BigDecimal.ZERO) >= 0, "嘴巴张开率应>=0");
        assertTrue(result.getMouthOpenRatio().compareTo(new BigDecimal("0.45")) < 0,
                "正常驾驶嘴巴张开率应<0.45，实际: " + result.getMouthOpenRatio());

        assertNotNull(result.getIsFatigued(), "疲劳标记不应为空");
        assertFalse(Boolean.TRUE.equals(result.getIsFatigued()), "单帧正常驾驶不应检测到疲劳");
        assertNotNull(result.getFatigueConfidence(), "疲劳置信度不应为空");
        assertTrue(result.getFatigueConfidence().compareTo(BigDecimal.ZERO) >= 0, "疲劳置信度应>=0");
        assertNotNull(result.getEyeAspectRatio(), "眼睛纵横比不应为空");
        assertTrue(result.getEyeAspectRatio().compareTo(BigDecimal.ZERO) > 0, "眼睛纵横比应>0");
        assertTrue(result.getEyeAspectRatio().compareTo(new BigDecimal("0.20")) > 0,
                "正常驾驶眼睛纵横比应>0.20，实际: " + result.getEyeAspectRatio());
        assertNotNull(result.getPerclosScore(), "PERCLOS不应为空");
        assertTrue(result.getPerclosScore().compareTo(BigDecimal.ZERO) >= 0, "PERCLOS应>=0");

        assertNotNull(result.getIsDistracted(), "分心标记不应为空");
        assertFalse(Boolean.TRUE.equals(result.getIsDistracted()), "正常驾驶不应检测到分心");
        assertNotNull(result.getDistractionConfidence(), "分心置信度不应为空");
        assertTrue(result.getDistractionConfidence().compareTo(BigDecimal.ZERO) >= 0, "分心置信度应>=0");
        assertNotNull(result.getHeadPoseYaw(), "头部偏航角不应为空");
        assertTrue(result.getHeadPoseYaw().abs().compareTo(new BigDecimal("25")) < 0,
                "正常驾驶头部偏航角绝对值应<25，实际: " + result.getHeadPoseYaw());
        assertNotNull(result.getHeadPosePitch(), "头部俯仰角不应为空");
        assertTrue(result.getHeadPosePitch().abs().compareTo(new BigDecimal("15")) < 0,
                "正常驾驶头部俯仰角绝对值应<15，实际: " + result.getHeadPosePitch());

        assertNotNull(result.getOverallScore(), "综合评分不应为空");
        assertTrue(result.getOverallScore().compareTo(BigDecimal.ZERO) >= 0, "综合评分应>=0");
        assertTrue(result.getOverallScore().compareTo(BigDecimal.valueOf(100)) <= 0, "综合评分应<=100");
        assertTrue(result.getOverallScore().compareTo(BigDecimal.valueOf(60)) > 0,
                "正常驾驶综合评分应>60，实际: " + result.getOverallScore());

        assertNotNull(result.getBehaviorLevel(), "行为等级不应为空");
        assertTrue(result.getBehaviorLevel() >= 1 && result.getBehaviorLevel() <= 5,
                "行为等级应在1-5之间，实际: " + result.getBehaviorLevel());
        assertTrue(result.getBehaviorLevel() <= 2,
                "正常驾驶行为等级应为1或2，实际: " + result.getBehaviorLevel());

        assertNotNull(result.getIsAbnormal(), "异常标记不应为空");
        assertFalse(Boolean.TRUE.equals(result.getIsAbnormal()), "正常驾驶不应标记为异常");
        assertTrue(result.getAbnormalTypes() == null || result.getAbnormalTypes().isEmpty(),
                "正常驾驶异常类型应为空，实际: " + result.getAbnormalTypes());

        assertNotNull(result.getDescription(), "描述不应为空");
        assertTrue(result.getDescription().contains("良好") || result.getDescription().contains("集中"),
                "正常驾驶描述应包含状态良好相关信息，实际: " + result.getDescription());

        assertNotNull(result.getDetectionDurationMs(), "检测耗时不应为空");
        assertTrue(result.getDetectionDurationMs() >= 0, "检测耗时应>=0");
        assertTrue(result.getDetectionDurationMs() < 10000, "检测耗时应<10000ms");
    }

    @Test
    @DisplayName("真实图像测试-打电话场景")
    void testAnalyze_PhoneCall() throws IOException {
        BufferedImage frame = loadImage("02-phone-call.png");
        assertNotNull(frame, "打电话图像应加载成功");

        Long cameraId = 2L;
        String cameraName = "打电话检测摄像头";
        analyzer.clearFrameHistory(cameraId);

        DriverBehaviorAnalysisResult result = analyzer.analyze(cameraId, cameraName, frame, null);

        System.out.println("=== 打电话场景分析结果 ===");
        System.out.println("打电话置信度: " + result.getPhoneCallConfidence() + "%");
        System.out.println("是否打电话: " + result.getIsPhoneCall());
        System.out.println("综合评分: " + result.getOverallScore());
        System.out.println("是否异常: " + result.getIsAbnormal());
        System.out.println("异常类型: " + result.getAbnormalTypes());
        System.out.println("描述: " + result.getDescription());

        assertNotNull(result);
        assertEquals(cameraId, result.getCameraId());
        assertEquals(cameraName, result.getCameraName());

        assertNotNull(result.getIsPhoneCall());
        assertTrue(Boolean.TRUE.equals(result.getIsPhoneCall()),
                "应检测到打电话行为，置信度: " + result.getPhoneCallConfidence());
        assertNotNull(result.getPhoneCallConfidence());
        assertTrue(result.getPhoneCallConfidence().compareTo(new BigDecimal("70")) >= 0,
                "打电话置信度应>=70，实际: " + result.getPhoneCallConfidence());
        assertTrue(result.getPhoneCallConfidence().compareTo(BigDecimal.valueOf(100)) <= 0,
                "打电话置信度应<=100，实际: " + result.getPhoneCallConfidence());

        assertNotNull(result.getIsYawning());
        assertFalse(Boolean.TRUE.equals(result.getIsYawning()),
                "打电话场景不应检测到打哈欠");
        assertNotNull(result.getIsFatigued());
        assertFalse(Boolean.TRUE.equals(result.getIsFatigued()),
                "打电话单帧不应检测到疲劳");

        assertNotNull(result.getIsDistracted());
        assertNotNull(result.getHeadPoseYaw());

        assertNotNull(result.getIsAbnormal());
        assertTrue(Boolean.TRUE.equals(result.getIsAbnormal()),
                "打电话应标记为异常");
        assertNotNull(result.getAbnormalTypes());
        assertTrue(result.getAbnormalTypes().contains("PHONE_CALL"),
                "异常类型应包含PHONE_CALL，实际: " + result.getAbnormalTypes());

        assertNotNull(result.getOverallScore());
        assertTrue(result.getOverallScore().compareTo(BigDecimal.valueOf(60)) <= 0,
                "打电话综合评分应<=60，实际: " + result.getOverallScore());

        assertNotNull(result.getBehaviorLevel());
        assertTrue(result.getBehaviorLevel() >= 3,
                "打电话行为等级应>=3，实际: " + result.getBehaviorLevel());

        assertNotNull(result.getDescription());
        assertTrue(result.getDescription().contains("打电话"),
                "描述应包含打电话信息，实际: " + result.getDescription());

        assertNotNull(result.getAlgorithmVersion());
        assertNotNull(result.getDetectionTime());
        assertNotNull(result.getDetectionDurationMs());
    }

    @Test
    @DisplayName("真实图像测试-打哈欠场景")
    void testAnalyze_Yawning() throws IOException {
        BufferedImage frame = loadImage("03-yawning.png");
        assertNotNull(frame, "打哈欠图像应加载成功");

        Long cameraId = 3L;
        String cameraName = "打哈欠检测摄像头";
        analyzer.clearFrameHistory(cameraId);

        DriverBehaviorAnalysisResult result = analyzer.analyze(cameraId, cameraName, frame, null);

        System.out.println("=== 打哈欠场景分析结果 ===");
        System.out.println("打哈欠置信度: " + result.getYawningConfidence() + "%");
        System.out.println("嘴巴张开率: " + result.getMouthOpenRatio());
        System.out.println("是否打哈欠: " + result.getIsYawning());
        System.out.println("综合评分: " + result.getOverallScore());
        System.out.println("是否异常: " + result.getIsAbnormal());
        System.out.println("异常类型: " + result.getAbnormalTypes());
        System.out.println("描述: " + result.getDescription());

        assertNotNull(result);
        assertEquals(cameraId, result.getCameraId());
        assertEquals(cameraName, result.getCameraName());

        assertNotNull(result.getIsYawning());
        assertTrue(Boolean.TRUE.equals(result.getIsYawning()),
                "应检测到打哈欠行为，置信度: " + result.getYawningConfidence()
                        + "，张口率: " + result.getMouthOpenRatio());
        assertNotNull(result.getYawningConfidence());
        assertTrue(result.getYawningConfidence().compareTo(BigDecimal.ZERO) >= 0,
                "打哈欠置信度应>=0");
        assertNotNull(result.getMouthOpenRatio());
        assertTrue(result.getMouthOpenRatio().compareTo(new BigDecimal("0.45")) >= 0,
                "打哈欠嘴巴张开率应>=0.45，实际: " + result.getMouthOpenRatio());
        assertTrue(result.getMouthOpenRatio().compareTo(BigDecimal.ONE) <= 0,
                "嘴巴张开率应<=1，实际: " + result.getMouthOpenRatio());

        assertNotNull(result.getIsPhoneCall());
        assertFalse(Boolean.TRUE.equals(result.getIsPhoneCall()),
                "打哈欠场景不应检测到打电话");
        assertNotNull(result.getIsFatigued());
        assertFalse(Boolean.TRUE.equals(result.getIsFatigued()),
                "打哈欠单帧不应检测到疲劳");
        assertNotNull(result.getIsDistracted());

        assertNotNull(result.getIsAbnormal());
        assertTrue(Boolean.TRUE.equals(result.getIsAbnormal()),
                "打哈欠应标记为异常");
        assertNotNull(result.getAbnormalTypes());
        assertTrue(result.getAbnormalTypes().contains("YAWNING"),
                "异常类型应包含YAWNING，实际: " + result.getAbnormalTypes());

        assertNotNull(result.getOverallScore());
        assertTrue(result.getOverallScore().compareTo(BigDecimal.valueOf(70)) <= 0,
                "打哈欠综合评分应<=70，实际: " + result.getOverallScore());

        assertNotNull(result.getBehaviorLevel());
        assertTrue(result.getBehaviorLevel() >= 2,
                "打哈欠行为等级应>=2，实际: " + result.getBehaviorLevel());

        assertNotNull(result.getDescription());
        assertTrue(result.getDescription().contains("打哈欠"),
                "描述应包含打哈欠信息，实际: " + result.getDescription());

        assertNotNull(result.getAlgorithmVersion());
        assertNotNull(result.getDetectionTime());
        assertNotNull(result.getDetectionDurationMs());
    }

    @Test
    @DisplayName("真实图像测试-疲劳驾驶场景")
    void testAnalyze_Fatigued() throws IOException {
        BufferedImage fatiguedFrame = loadImage("04-fatigued.png");
        BufferedImage normalFrame = loadImage("01-normal-driver.png");
        assertNotNull(fatiguedFrame, "疲劳驾驶图像应加载成功");
        assertNotNull(normalFrame, "正常驾驶图像应加载成功");

        Long cameraId = 4L;
        String cameraName = "疲劳检测摄像头";
        analyzer.clearFrameHistory(cameraId);

        System.out.println("=== 疲劳驾驶场景-累积帧历史 ===");
        DriverBehaviorAnalysisResult lastResult = null;
        for (int i = 0; i < 35; i++) {
            lastResult = analyzer.analyze(cameraId, cameraName, fatiguedFrame, normalFrame);
            if (i < 5 || i % 5 == 4) {
                System.out.println("第" + (i + 1) + "帧: PERCLOS=" + lastResult.getPerclosScore()
                        + "%, EAR=" + lastResult.getEyeAspectRatio()
                        + ", 疲劳=" + lastResult.getIsFatigued());
            }
        }

        assertNotNull(lastResult);

        System.out.println("=== 疲劳驾驶场景-最终分析结果 ===");
        System.out.println("疲劳置信度: " + lastResult.getFatigueConfidence() + "%");
        System.out.println("眼睛纵横比: " + lastResult.getEyeAspectRatio());
        System.out.println("PERCLOS: " + lastResult.getPerclosScore() + "%");
        System.out.println("是否疲劳: " + lastResult.getIsFatigued());
        System.out.println("综合评分: " + lastResult.getOverallScore());
        System.out.println("是否异常: " + lastResult.getIsAbnormal());
        System.out.println("异常类型: " + lastResult.getAbnormalTypes());
        System.out.println("描述: " + lastResult.getDescription());

        assertEquals(cameraId, lastResult.getCameraId());
        assertEquals(cameraName, lastResult.getCameraName());

        assertNotNull(lastResult.getEyeAspectRatio());
        assertTrue(lastResult.getEyeAspectRatio().compareTo(new BigDecimal("0.20")) <= 0,
                "疲劳驾驶眼睛纵横比应<=0.20，实际: " + lastResult.getEyeAspectRatio());
        assertTrue(lastResult.getEyeAspectRatio().compareTo(BigDecimal.ZERO) >= 0,
                "眼睛纵横比应>=0");

        assertNotNull(lastResult.getPerclosScore());
        assertTrue(lastResult.getPerclosScore().compareTo(new BigDecimal("50")) >= 0,
                "累积30+帧疲劳后PERCLOS应>=50，实际: " + lastResult.getPerclosScore());
        assertTrue(lastResult.getPerclosScore().compareTo(BigDecimal.valueOf(100)) <= 0,
                "PERCLOS应<=100，实际: " + lastResult.getPerclosScore());

        assertNotNull(lastResult.getIsFatigued());
        assertTrue(Boolean.TRUE.equals(lastResult.getIsFatigued()),
                "应检测到疲劳驾驶，PERCLOS: " + lastResult.getPerclosScore()
                        + "，置信度: " + lastResult.getFatigueConfidence());
        assertNotNull(lastResult.getFatigueConfidence());
        assertTrue(lastResult.getFatigueConfidence().compareTo(new BigDecimal("65")) >= 0,
                "疲劳置信度应>=65，实际: " + lastResult.getFatigueConfidence());

        assertNotNull(lastResult.getIsAbnormal());
        assertTrue(Boolean.TRUE.equals(lastResult.getIsAbnormal()),
                "疲劳驾驶应标记为异常");
        assertNotNull(lastResult.getAbnormalTypes());
        assertTrue(lastResult.getAbnormalTypes().contains("FATIGUE"),
                "异常类型应包含FATIGUE，实际: " + lastResult.getAbnormalTypes());

        assertNotNull(lastResult.getOverallScore());
        assertTrue(lastResult.getOverallScore().compareTo(BigDecimal.valueOf(50)) <= 0,
                "疲劳驾驶综合评分应<=50，实际: " + lastResult.getOverallScore());

        assertNotNull(lastResult.getBehaviorLevel());
        assertTrue(lastResult.getBehaviorLevel() >= 4,
                "疲劳驾驶行为等级应>=4，实际: " + lastResult.getBehaviorLevel());

        assertNotNull(lastResult.getDescription());
        assertTrue(lastResult.getDescription().contains("疲劳"),
                "描述应包含疲劳信息，实际: " + lastResult.getDescription());

        assertNotNull(lastResult.getAlgorithmVersion());
        assertNotNull(lastResult.getDetectionTime());
        assertNotNull(lastResult.getDetectionDurationMs());

        assertNotNull(lastResult.getIsPhoneCall());
        assertNotNull(lastResult.getPhoneCallConfidence());
        assertNotNull(lastResult.getIsYawning());
        assertNotNull(lastResult.getYawningConfidence());
        assertNotNull(lastResult.getMouthOpenRatio());
        assertNotNull(lastResult.getIsDistracted());
        assertNotNull(lastResult.getDistractionConfidence());
        assertNotNull(lastResult.getHeadPoseYaw());
        assertNotNull(lastResult.getHeadPosePitch());
    }

    @Test
    @DisplayName("真实图像测试-分心驾驶场景")
    void testAnalyze_Distracted() throws IOException {
        BufferedImage leftFrame = loadImage("05-distracted-left.png");
        BufferedImage rightFrame = loadImage("06-distracted-right.png");
        assertNotNull(leftFrame, "分心驾驶-左侧图像应加载成功");
        assertNotNull(rightFrame, "分心驾驶-右侧图像应加载成功");

        Long cameraIdLeft = 5L;
        Long cameraIdRight = 6L;
        String cameraNameLeft = "分心检测-左侧";
        String cameraNameRight = "分心检测-右侧";
        analyzer.clearFrameHistory(cameraIdLeft);
        analyzer.clearFrameHistory(cameraIdRight);

        DriverBehaviorAnalysisResult leftResult = analyzer.analyze(cameraIdLeft, cameraNameLeft, leftFrame, null);
        DriverBehaviorAnalysisResult rightResult = analyzer.analyze(cameraIdRight, cameraNameRight, rightFrame, null);

        System.out.println("=== 分心驾驶场景-左侧分析结果 ===");
        System.out.println("分心置信度: " + leftResult.getDistractionConfidence() + "%");
        System.out.println("头部偏航: " + leftResult.getHeadPoseYaw() + "°");
        System.out.println("头部俯仰: " + leftResult.getHeadPosePitch() + "°");
        System.out.println("是否分心: " + leftResult.getIsDistracted());
        System.out.println("是否异常: " + leftResult.getIsAbnormal());
        System.out.println("异常类型: " + leftResult.getAbnormalTypes());

        System.out.println("=== 分心驾驶场景-右侧分析结果 ===");
        System.out.println("分心置信度: " + rightResult.getDistractionConfidence() + "%");
        System.out.println("头部偏航: " + rightResult.getHeadPoseYaw() + "°");
        System.out.println("头部俯仰: " + rightResult.getHeadPosePitch() + "°");
        System.out.println("是否分心: " + rightResult.getIsDistracted());
        System.out.println("是否异常: " + rightResult.getIsAbnormal());
        System.out.println("异常类型: " + rightResult.getAbnormalTypes());

        assertNotNull(leftResult);
        assertNotNull(rightResult);

        assertNotNull(leftResult.getHeadPoseYaw());
        assertTrue(leftResult.getHeadPoseYaw().compareTo(BigDecimal.ZERO) < 0,
                "左侧分心偏航角应为负值，实际: " + leftResult.getHeadPoseYaw());
        assertTrue(leftResult.getHeadPoseYaw().abs().compareTo(new BigDecimal("25")) >= 0,
                "左侧分心偏航角绝对值应>=25，实际: " + leftResult.getHeadPoseYaw());

        assertNotNull(rightResult.getHeadPoseYaw());
        assertTrue(rightResult.getHeadPoseYaw().compareTo(BigDecimal.ZERO) > 0,
                "右侧分心偏航角应为正值，实际: " + rightResult.getHeadPoseYaw());
        assertTrue(rightResult.getHeadPoseYaw().abs().compareTo(new BigDecimal("25")) >= 0,
                "右侧分心偏航角绝对值应>=25，实际: " + rightResult.getHeadPoseYaw());

        assertNotNull(leftResult.getIsDistracted());
        assertTrue(Boolean.TRUE.equals(leftResult.getIsDistracted()),
                "左侧分心应被检测到，偏航: " + leftResult.getHeadPoseYaw());
        assertNotNull(rightResult.getIsDistracted());
        assertTrue(Boolean.TRUE.equals(rightResult.getIsDistracted()),
                "右侧分心应被检测到，偏航: " + rightResult.getHeadPoseYaw());

        assertNotNull(leftResult.getDistractionConfidence());
        assertTrue(leftResult.getDistractionConfidence().compareTo(new BigDecimal("65")) >= 0,
                "左侧分心置信度应>=65，实际: " + leftResult.getDistractionConfidence());
        assertNotNull(rightResult.getDistractionConfidence());
        assertTrue(rightResult.getDistractionConfidence().compareTo(new BigDecimal("65")) >= 0,
                "右侧分心置信度应>=65，实际: " + rightResult.getDistractionConfidence());

        assertNotNull(leftResult.getHeadPosePitch());
        assertNotNull(rightResult.getHeadPosePitch());

        assertNotNull(leftResult.getIsAbnormal());
        assertTrue(Boolean.TRUE.equals(leftResult.getIsAbnormal()),
                "左侧分心应标记为异常");
        assertNotNull(rightResult.getIsAbnormal());
        assertTrue(Boolean.TRUE.equals(rightResult.getIsAbnormal()),
                "右侧分心应标记为异常");

        assertNotNull(leftResult.getAbnormalTypes());
        assertTrue(leftResult.getAbnormalTypes().contains("DISTRACTION"),
                "左侧异常类型应包含DISTRACTION，实际: " + leftResult.getAbnormalTypes());
        assertNotNull(rightResult.getAbnormalTypes());
        assertTrue(rightResult.getAbnormalTypes().contains("DISTRACTION"),
                "右侧异常类型应包含DISTRACTION，实际: " + rightResult.getAbnormalTypes());

        assertNotNull(leftResult.getOverallScore());
        assertTrue(leftResult.getOverallScore().compareTo(BigDecimal.valueOf(70)) <= 0,
                "左侧分心综合评分应<=70，实际: " + leftResult.getOverallScore());
        assertNotNull(rightResult.getOverallScore());
        assertTrue(rightResult.getOverallScore().compareTo(BigDecimal.valueOf(70)) <= 0,
                "右侧分心综合评分应<=70，实际: " + rightResult.getOverallScore());

        assertNotNull(leftResult.getBehaviorLevel());
        assertTrue(leftResult.getBehaviorLevel() >= 2,
                "左侧分心行为等级应>=2，实际: " + leftResult.getBehaviorLevel());
        assertNotNull(rightResult.getBehaviorLevel());
        assertTrue(rightResult.getBehaviorLevel() >= 2,
                "右侧分心行为等级应>=2，实际: " + rightResult.getBehaviorLevel());

        assertNotNull(leftResult.getDescription());
        assertTrue(leftResult.getDescription().contains("分心"),
                "左侧描述应包含分心信息，实际: " + leftResult.getDescription());
        assertNotNull(rightResult.getDescription());
        assertTrue(rightResult.getDescription().contains("分心"),
                "右侧描述应包含分心信息，实际: " + rightResult.getDescription());

        assertEquals(cameraIdLeft, leftResult.getCameraId());
        assertEquals(cameraNameLeft, leftResult.getCameraName());
        assertEquals(cameraIdRight, rightResult.getCameraId());
        assertEquals(cameraNameRight, rightResult.getCameraName());

        assertNotNull(leftResult.getAlgorithmVersion());
        assertNotNull(rightResult.getAlgorithmVersion());
        assertNotNull(leftResult.getDetectionTime());
        assertNotNull(rightResult.getDetectionTime());
        assertNotNull(leftResult.getDetectionDurationMs());
        assertNotNull(rightResult.getDetectionDurationMs());
    }

    @Test
    @DisplayName("Null帧测试-异常处理")
    void testAnalyze_NullFrame() {
        Long cameraId = 99L;
        String cameraName = "离线摄像头";

        DriverBehaviorAnalysisResult result = analyzer.analyze(cameraId, cameraName, null, null);

        System.out.println("=== Null帧分析结果 ===");
        System.out.println("是否异常: " + result.getIsAbnormal());
        System.out.println("异常类型: " + result.getAbnormalTypes());
        System.out.println("综合评分: " + result.getOverallScore());
        System.out.println("行为等级: " + result.getBehaviorLevel());
        System.out.println("描述: " + result.getDescription());

        assertNotNull(result, "Null帧分析结果不应为空");
        assertEquals(cameraId, result.getCameraId(), "摄像头ID应匹配");
        assertEquals(cameraName, result.getCameraName(), "摄像头名称应匹配");
        assertNotNull(result.getDetectionTime(), "检测时间不应为空");
        assertNotNull(result.getAlgorithmVersion(), "算法版本不应为空");

        assertNotNull(result.getIsAbnormal(), "异常标记不应为空");
        assertTrue(Boolean.TRUE.equals(result.getIsAbnormal()), "Null帧应标记为异常");

        assertNotNull(result.getAbnormalTypes(), "异常类型不应为空");
        assertTrue(result.getAbnormalTypes().contains("NO_FRAME"),
                "异常类型应包含NO_FRAME，实际: " + result.getAbnormalTypes());

        assertNotNull(result.getOverallScore(), "综合评分不应为空");
        assertEquals(BigDecimal.ZERO, result.getOverallScore(), "Null帧综合评分应为0");

        assertNotNull(result.getBehaviorLevel(), "行为等级不应为空");
        assertEquals(5, result.getBehaviorLevel(), "Null帧行为等级应为5(最严重)");

        assertNotNull(result.getDescription(), "描述不应为空");
        assertTrue(result.getDescription().contains("无法获取") || result.getDescription().contains("离线"),
                "描述应包含无法获取帧或离线信息，实际: " + result.getDescription());

        assertNotNull(result.getDetectionDurationMs(), "检测耗时不应为空");
        assertTrue(result.getDetectionDurationMs() >= 0, "检测耗时应>=0");

        assertNotNull(result.getIsPhoneCall());
        assertFalse(Boolean.TRUE.equals(result.getIsPhoneCall()), "Null帧不应检测到打电话");
        assertNotNull(result.getIsYawning());
        assertFalse(Boolean.TRUE.equals(result.getIsYawning()), "Null帧不应检测到打哈欠");
        assertNotNull(result.getIsFatigued());
        assertFalse(Boolean.TRUE.equals(result.getIsFatigued()), "Null帧不应检测到疲劳");
        assertNotNull(result.getIsDistracted());
        assertFalse(Boolean.TRUE.equals(result.getIsDistracted()), "Null帧不应检测到分心");
    }

    @Test
    @DisplayName("帧历史清除测试")
    void testClearFrameHistory() throws IOException {
        BufferedImage fatiguedFrame = loadImage("04-fatigued.png");
        BufferedImage normalFrame = loadImage("01-normal-driver.png");
        assertNotNull(fatiguedFrame);
        assertNotNull(normalFrame);

        Long cameraId = 100L;
        String cameraName = "历史清除测试摄像头";
        analyzer.clearFrameHistory(cameraId);

        System.out.println("=== 帧历史清除测试 ===");

        System.out.println("阶段1: 累积疲劳帧历史...");
        DriverBehaviorAnalysisResult fatiguedResult = null;
        for (int i = 0; i < 25; i++) {
            fatiguedResult = analyzer.analyze(cameraId, cameraName, fatiguedFrame, normalFrame);
        }
        assertNotNull(fatiguedResult);
        System.out.println("累积25帧后 PERCLOS: " + fatiguedResult.getPerclosScore()
                + "%, 是否疲劳: " + fatiguedResult.getIsFatigued());
        assertNotNull(fatiguedResult.getPerclosScore());
        assertTrue(fatiguedResult.getPerclosScore().compareTo(BigDecimal.ZERO) > 0,
                "累积帧后PERCLOS应>0，实际: " + fatiguedResult.getPerclosScore());

        System.out.println("阶段2: 清除帧历史...");
        analyzer.clearFrameHistory(cameraId);

        System.out.println("阶段3: 使用正常帧重新检测...");
        DriverBehaviorAnalysisResult clearedResult = analyzer.analyze(cameraId, cameraName, normalFrame, fatiguedFrame);
        assertNotNull(clearedResult);
        System.out.println("清除后第一帧 PERCLOS: " + clearedResult.getPerclosScore()
                + "%, 是否疲劳: " + clearedResult.getIsFatigued());

        assertNotNull(clearedResult.getPerclosScore());
        assertTrue(clearedResult.getPerclosScore().compareTo(new BigDecimal("50")) < 0,
                "清除历史后PERCLOS应重新累积，不应立即达到50，实际: " + clearedResult.getPerclosScore());

        assertNotNull(clearedResult.getIsFatigued());
        assertFalse(Boolean.TRUE.equals(clearedResult.getIsFatigued()),
                "清除历史后使用正常帧不应立即检测为疲劳");

        System.out.println("阶段4: 验证clearAllFrameHistory...");
        Long cameraId2 = 101L;
        for (int i = 0; i < 10; i++) {
            analyzer.analyze(cameraId2, "摄像头2", fatiguedFrame, normalFrame);
        }
        analyzer.clearAllFrameHistory();

        DriverBehaviorAnalysisResult allClearedResult = analyzer.analyze(cameraId2, "摄像头2", normalFrame, fatiguedFrame);
        assertNotNull(allClearedResult);
        assertNotNull(allClearedResult.getPerclosScore());
        assertTrue(allClearedResult.getPerclosScore().compareTo(new BigDecimal("50")) < 0,
                "clearAllFrameHistory后所有摄像头历史应被清除");

        System.out.println("帧历史清除测试通过！");
    }

    @Test
    @DisplayName("所有真实图像算法输出完整性验证")
    void testAllRealImages_OutputCompleteness() throws IOException {
        String[] testImages = {
                "01-normal-driver.png",
                "02-phone-call.png",
                "03-yawning.png",
                "04-fatigued.png",
                "05-distracted-left.png",
                "06-distracted-right.png"
        };

        int totalAssertions = 0;

        for (int i = 0; i < testImages.length; i++) {
            String imageName = testImages[i];
            System.out.println("=== 完整性验证: " + imageName + " ===");

            BufferedImage frame = loadImage(imageName);
            assertNotNull(frame, imageName + " 图像应加载成功");
            totalAssertions++;

            Long cameraId = 1000L + i;
            String cameraName = "完整性测试-" + imageName;

            DriverBehaviorAnalysisResult result = analyzer.analyze(cameraId, cameraName, frame, null);
            assertNotNull(result, imageName + " 结果不应为空");
            totalAssertions++;

            assertNotNull(result.getCameraId(), imageName + " cameraId不应为空");
            totalAssertions++;
            assertEquals(cameraId, result.getCameraId(), imageName + " cameraId应匹配");
            totalAssertions++;

            assertNotNull(result.getCameraName(), imageName + " cameraName不应为空");
            totalAssertions++;
            assertEquals(cameraName, result.getCameraName(), imageName + " cameraName应匹配");
            totalAssertions++;

            assertNotNull(result.getDetectionTime(), imageName + " detectionTime不应为空");
            totalAssertions++;
            assertNotNull(result.getAlgorithmVersion(), imageName + " algorithmVersion不应为空");
            totalAssertions++;

            assertNotNull(result.getIsPhoneCall(), imageName + " isPhoneCall不应为空");
            totalAssertions++;
            assertNotNull(result.getPhoneCallConfidence(), imageName + " phoneCallConfidence不应为空");
            totalAssertions++;
            assertTrue(result.getPhoneCallConfidence().compareTo(BigDecimal.ZERO) >= 0,
                    imageName + " phoneCallConfidence应>=0");
            totalAssertions++;
            assertTrue(result.getPhoneCallConfidence().compareTo(BigDecimal.valueOf(100)) <= 0,
                    imageName + " phoneCallConfidence应<=100");
            totalAssertions++;

            assertNotNull(result.getIsYawning(), imageName + " isYawning不应为空");
            totalAssertions++;
            assertNotNull(result.getYawningConfidence(), imageName + " yawningConfidence不应为空");
            totalAssertions++;
            assertTrue(result.getYawningConfidence().compareTo(BigDecimal.ZERO) >= 0,
                    imageName + " yawningConfidence应>=0");
            totalAssertions++;
            assertTrue(result.getYawningConfidence().compareTo(BigDecimal.valueOf(100)) <= 0,
                    imageName + " yawningConfidence应<=100");
            totalAssertions++;
            assertNotNull(result.getMouthOpenRatio(), imageName + " mouthOpenRatio不应为空");
            totalAssertions++;
            assertTrue(result.getMouthOpenRatio().compareTo(BigDecimal.ZERO) >= 0,
                    imageName + " mouthOpenRatio应>=0");
            totalAssertions++;

            assertNotNull(result.getIsFatigued(), imageName + " isFatigued不应为空");
            totalAssertions++;
            assertNotNull(result.getFatigueConfidence(), imageName + " fatigueConfidence不应为空");
            totalAssertions++;
            assertTrue(result.getFatigueConfidence().compareTo(BigDecimal.ZERO) >= 0,
                    imageName + " fatigueConfidence应>=0");
            totalAssertions++;
            assertTrue(result.getFatigueConfidence().compareTo(BigDecimal.valueOf(100)) <= 0,
                    imageName + " fatigueConfidence应<=100");
            totalAssertions++;
            assertNotNull(result.getEyeAspectRatio(), imageName + " eyeAspectRatio不应为空");
            totalAssertions++;
            assertTrue(result.getEyeAspectRatio().compareTo(BigDecimal.ZERO) >= 0,
                    imageName + " eyeAspectRatio应>=0");
            totalAssertions++;
            assertNotNull(result.getPerclosScore(), imageName + " perclosScore不应为空");
            totalAssertions++;
            assertTrue(result.getPerclosScore().compareTo(BigDecimal.ZERO) >= 0,
                    imageName + " perclosScore应>=0");
            totalAssertions++;
            assertTrue(result.getPerclosScore().compareTo(BigDecimal.valueOf(100)) <= 0,
                    imageName + " perclosScore应<=100");
            totalAssertions++;

            assertNotNull(result.getIsDistracted(), imageName + " isDistracted不应为空");
            totalAssertions++;
            assertNotNull(result.getDistractionConfidence(), imageName + " distractionConfidence不应为空");
            totalAssertions++;
            assertTrue(result.getDistractionConfidence().compareTo(BigDecimal.ZERO) >= 0,
                    imageName + " distractionConfidence应>=0");
            totalAssertions++;
            assertTrue(result.getDistractionConfidence().compareTo(BigDecimal.valueOf(100)) <= 0,
                    imageName + " distractionConfidence应<=100");
            totalAssertions++;
            assertNotNull(result.getHeadPoseYaw(), imageName + " headPoseYaw不应为空");
            totalAssertions++;
            assertNotNull(result.getHeadPosePitch(), imageName + " headPosePitch不应为空");
            totalAssertions++;

            assertNotNull(result.getOverallScore(), imageName + " overallScore不应为空");
            totalAssertions++;
            assertTrue(result.getOverallScore().compareTo(BigDecimal.ZERO) >= 0,
                    imageName + " overallScore应>=0");
            totalAssertions++;
            assertTrue(result.getOverallScore().compareTo(BigDecimal.valueOf(100)) <= 0,
                    imageName + " overallScore应<=100");
            totalAssertions++;

            assertNotNull(result.getBehaviorLevel(), imageName + " behaviorLevel不应为空");
            totalAssertions++;
            assertTrue(result.getBehaviorLevel() >= 1 && result.getBehaviorLevel() <= 5,
                    imageName + " behaviorLevel应在1-5之间");
            totalAssertions++;

            assertNotNull(result.getIsAbnormal(), imageName + " isAbnormal不应为空");
            totalAssertions++;
            assertNotNull(result.getDescription(), imageName + " description不应为空");
            totalAssertions++;
            assertFalse(result.getDescription().isEmpty(), imageName + " description不应为空字符串");
            totalAssertions++;

            assertNotNull(result.getDetectionDurationMs(), imageName + " detectionDurationMs不应为空");
            totalAssertions++;
            assertTrue(result.getDetectionDurationMs() >= 0,
                    imageName + " detectionDurationMs应>=0");
            totalAssertions++;
        }

        System.out.println("完整性验证完成，总计断言数: " + totalAssertions);
        assertTrue(totalAssertions >= 100,
                "完整性验证断言数应>=100，实际: " + totalAssertions);
    }
}
