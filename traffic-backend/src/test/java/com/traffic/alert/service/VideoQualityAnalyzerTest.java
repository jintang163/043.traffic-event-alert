package com.traffic.alert.service;

import com.traffic.alert.config.VideoQualityConfig;
import com.traffic.alert.dto.VideoQualityAnalysisResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class VideoQualityAnalyzerTest {

    private VideoQualityAnalyzer analyzer;
    private VideoQualityConfig config;

    @BeforeEach
    void setUp() {
        config = new VideoQualityConfig();
        VideoQualityConfig.Thresholds thresholds = new VideoQualityConfig.Thresholds();
        thresholds.setBlackScreenBrightness(BigDecimal.valueOf(15));
        thresholds.setLowBrightness(BigDecimal.valueOf(40));
        thresholds.setHighBrightness(BigDecimal.valueOf(220));
        thresholds.setLowContrast(BigDecimal.valueOf(35));
        thresholds.setHighContrast(BigDecimal.valueOf(95));
        thresholds.setSevereBlur(BigDecimal.valueOf(0.35));
        thresholds.setSlightBlur(BigDecimal.valueOf(0.65));
        thresholds.setSevereOcclusion(BigDecimal.valueOf(30));
        thresholds.setSlightOcclusion(BigDecimal.valueOf(10));
        thresholds.setFreezeFrameChange(BigDecimal.valueOf(0.02));
        thresholds.setFreezeMinFrames(5);
        thresholds.setSevereColorCast(BigDecimal.valueOf(0.25));
        thresholds.setSlightColorCast(BigDecimal.valueOf(0.12));
        config.setThresholds(thresholds);

        VideoQualityConfig.Scoring scoring = new VideoQualityConfig.Scoring();
        scoring.setBrightnessWeight(20);
        scoring.setContrastWeight(15);
        scoring.setBlurWeight(25);
        scoring.setOcclusionWeight(20);
        scoring.setFreezeWeight(15);
        scoring.setNoiseWeight(5);
        scoring.setExcellentMin(90);
        scoring.setGoodMin(75);
        scoring.setMediumMin(60);
        scoring.setPoorMin(40);
        scoring.setHealthExcellent(BigDecimal.valueOf(90));
        scoring.setHealthSubhealthy(BigDecimal.valueOf(75));
        scoring.setHealthAbnormal(BigDecimal.valueOf(50));
        scoring.setHealthCritical(BigDecimal.valueOf(25));
        config.setScoring(scoring);

        analyzer = new VideoQualityAnalyzer(config);
    }

    @Test
    @DisplayName("模拟检测-正常画面")
    void testAnalyzeMock_Normal() {
        VideoQualityAnalysisResult result = analyzer.analyzeMock(1L, "测试摄像头", 0);

        assertNotNull(result);
        assertEquals(1L, result.getCameraId());
        assertFalse(Boolean.TRUE.equals(result.getIsAbnormal()), "正常画面不应标记为异常");
        assertFalse(Boolean.TRUE.equals(result.getIsBlackScreen()), "正常画面不应黑屏");
        assertFalse(Boolean.TRUE.equals(result.getIsFrozen()), "正常画面不应冻结");
        assertEquals(1, result.getQualityLevel(), "正常画面质量等级应为优(1)");
        assertTrue(result.getOverallScore().compareTo(BigDecimal.valueOf(90)) >= 0,
                "正常画面综合评分应>=90");
        assertTrue(result.getBlurScore().compareTo(BigDecimal.valueOf(0.8)) >= 0,
                "正常画面模糊度评分应>=0.8");
        assertEquals(1, result.getBrightnessLevel(), "正常画面亮度等级应为正常(1)");
        assertEquals(1, result.getContrastLevel(), "正常画面对比度等级应为正常(1)");
        assertNull(result.getAbnormalTypes(), "正常画面异常类型应为空");
    }

    @Test
    @DisplayName("模拟检测-黑屏场景")
    void testAnalyzeMock_BlackScreen() {
        VideoQualityAnalysisResult result = analyzer.analyzeMock(1L, "测试摄像头", 1);

        assertNotNull(result);
        assertTrue(Boolean.TRUE.equals(result.getIsAbnormal()), "黑屏应标记为异常");
        assertTrue(Boolean.TRUE.equals(result.getIsBlackScreen()), "应检测到黑屏");
        assertEquals(4, result.getBrightnessLevel(), "黑屏亮度等级应为4");
        assertTrue(result.getOverallScore().compareTo(BigDecimal.valueOf(30)) <= 0,
                "黑屏综合评分应<=30");
        assertEquals(5, result.getQualityLevel(), "黑屏质量等级应为严重异常(5)");
        assertNotNull(result.getAbnormalTypes(), "异常类型不应为空");
        assertTrue(result.getAbnormalTypes().contains("BLACK_SCREEN"), "异常类型应包含BLACK_SCREEN");
        assertTrue(result.getDescription().contains("黑屏"), "描述应包含黑屏信息");
    }

    @Test
    @DisplayName("模拟检测-多异常场景(低亮+低对比度+模糊+遮挡)")
    void testAnalyzeMock_MultipleAbnormal() {
        VideoQualityAnalysisResult result = analyzer.analyzeMock(1L, "测试摄像头", 2);

        assertNotNull(result);
        assertTrue(Boolean.TRUE.equals(result.getIsAbnormal()), "应标记为异常");
        assertFalse(Boolean.TRUE.equals(result.getIsBlackScreen()), "不是黑屏");
        assertEquals(2, result.getBrightnessLevel(), "亮度等级应为偏暗(2)");
        assertEquals(2, result.getContrastLevel(), "对比度等级应为偏低(2)");
        assertTrue(result.getBlurLevel() >= 2, "模糊等级应>=2");
        assertTrue(result.getOcclusionLevel() >= 2, "遮挡等级应>=2");
        assertTrue(result.getOverallScore().compareTo(BigDecimal.valueOf(80)) < 0,
                "多异常综合评分应<80");
        assertNotNull(result.getAbnormalTypes());
        assertTrue(result.getAbnormalTypes().contains("LOW_BRIGHTNESS"), "应包含LOW_BRIGHTNESS");
        assertTrue(result.getAbnormalTypes().contains("LOW_CONTRAST"), "应包含LOW_CONTRAST");
        assertTrue(result.getAbnormalTypes().contains("OCCLUSION"), "应包含OCCLUSION");
    }

    @Test
    @DisplayName("模拟检测-严重遮挡")
    void testAnalyzeMock_SevereOcclusion() {
        VideoQualityAnalysisResult result = analyzer.analyzeMock(1L, "测试摄像头", 3);

        assertNotNull(result);
        assertTrue(Boolean.TRUE.equals(result.getIsAbnormal()), "严重遮挡应标记为异常");
        assertEquals(3, result.getOcclusionLevel(), "遮挡等级应为严重遮挡(3)");
        assertTrue(result.getOcclusionRatio().compareTo(BigDecimal.valueOf(30)) > 0,
                "遮挡率应>30%");
        assertNotNull(result.getAbnormalTypes());
        assertTrue(result.getAbnormalTypes().contains("OCCLUSION"), "异常类型应包含OCCLUSION");
        assertTrue(result.getQualityLevel() >= 3, "质量等级应>=3");
        assertTrue(result.getDescription().contains("遮挡"), "描述应包含遮挡信息");
    }

    @Test
    @DisplayName("模拟检测-画面冻结")
    void testAnalyzeMock_Freeze() {
        VideoQualityAnalysisResult result = analyzer.analyzeMock(1L, "测试摄像头", 4);

        assertNotNull(result);
        assertTrue(Boolean.TRUE.equals(result.getIsAbnormal()), "冻结应标记为异常");
        assertTrue(Boolean.TRUE.equals(result.getIsFrozen()), "应检测到冻结");
        assertNotNull(result.getFreezeDuration());
        assertTrue(result.getFreezeDuration() > 0, "冻结持续时间应>0");
        assertNotNull(result.getAbnormalTypes());
        assertTrue(result.getAbnormalTypes().contains("FREEZE"), "异常类型应包含FREEZE");
        assertTrue(result.getDescription().contains("冻结"), "描述应包含冻结信息");
    }

    @Test
    @DisplayName("检测耗时记录")
    void testAnalyzeMock_DurationRecorded() {
        VideoQualityAnalysisResult result = analyzer.analyzeMock(1L, "测试摄像头", 0);
        assertNotNull(result);
        assertNotNull(result.getDetectionDurationMs(), "检测耗时不应为空");
        assertTrue(result.getDetectionDurationMs() >= 0, "检测耗时应>=0");
        assertNotNull(result.getAlgorithmVersion(), "算法版本不应为空");
        assertNotNull(result.getDetectionTime(), "检测时间不应为空");
    }

    @Test
    @DisplayName("黑屏帧历史清除")
    void testClearFrameHistory() {
        analyzer.analyzeMock(100L, "测试清除", 1);
        analyzer.clearFrameHistory(100L);
        VideoQualityAnalysisResult result = analyzer.analyzeMock(100L, "测试清除", 0);
        assertNotNull(result);
        assertFalse(Boolean.TRUE.equals(result.getIsFrozen()), "清除历史后再次检测不应有冻结累积");
    }

    @Test
    @DisplayName("所有模拟场景覆盖")
    void testAllMockScenarios() {
        for (int i = 0; i <= 5; i++) {
            final int scenario = i;
            VideoQualityAnalysisResult result = analyzer.analyzeMock(
                    (long) scenario, "场景-" + scenario, scenario);
            assertNotNull(result, "场景" + scenario + "结果不应为空");
            assertNotNull(result.getCameraId(), "场景" + scenario + "cameraId不应为空");
            assertNotNull(result.getBrightness(), "场景" + scenario + "亮度值不应为空");
            assertNotNull(result.getContrast(), "场景" + scenario + "对比度值不应为空");
            assertNotNull(result.getBlurScore(), "场景" + scenario + "模糊评分不应为空");
            assertNotNull(result.getOcclusionRatio(), "场景" + scenario + "遮挡率不应为空");
            assertNotNull(result.getOverallScore(), "场景" + scenario + "综合评分不应为空");
            assertNotNull(result.getQualityLevel(), "场景" + scenario + "质量等级不应为空");
            assertNotNull(result.getIsAbnormal(), "场景" + scenario + "异常标记不应为空");
            assertNotNull(result.getBrightnessLevel(), "场景" + scenario + "亮度等级不应为空");
            assertNotNull(result.getContrastLevel(), "场景" + scenario + "对比度等级不应为空");
            assertNotNull(result.getBlurLevel(), "场景" + scenario + "模糊等级不应为空");
            assertNotNull(result.getOcclusionLevel(), "场景" + scenario + "遮挡等级不应为空");
        }
    }
}
