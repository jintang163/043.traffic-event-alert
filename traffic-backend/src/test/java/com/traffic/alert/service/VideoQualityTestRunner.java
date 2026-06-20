package com.traffic.alert.service;

import com.traffic.alert.config.VideoQualityConfig;
import com.traffic.alert.dto.VideoQualityAnalysisResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class VideoQualityTestRunner {

    private static VideoQualityAnalyzer analyzer;
    private static int passed = 0;
    private static int failed = 0;
    private static List<String> failures = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        setUp();

        System.out.println("========== 视频质量分析算法测试 ==========\n");

        testNormalScene();
        testBlackScreen();
        testLowQualityScene();
        testOccludedScene();
        testFreezeScene();
        testNonFreezeScene();
        testNullFrame();
        testClearFrameHistory();
        testOutputCompleteness();

        System.out.println("\n========== 测试结果汇总 ==========");
        System.out.println("通过: " + passed + " / " + (passed + failed));
        System.out.println("失败: " + failed);
        if (!failures.isEmpty()) {
            System.out.println("\n失败详情:");
            for (String f : failures) {
                System.out.println("  - " + f);
            }
        }
        System.out.println("==========================================");

        System.exit(failed > 0 ? 1 : 0);
    }

    private static void setUp() {
        VideoQualityConfig config = new VideoQualityConfig();
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
        thresholds.setFreezeMinFrames(3);
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

    private static BufferedImage loadImage(String filename) throws IOException {
        try (InputStream is = VideoQualityTestRunner.class.getClassLoader()
                .getResourceAsStream("video-quality-samples/" + filename)) {
            if (is == null) {
                throw new AssertionError("无法加载测试图像: " + filename);
            }
            return ImageIO.read(is);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            failed++;
            failures.add(message);
            System.out.println("  ❌ 失败: " + message);
        } else {
            passed++;
            System.out.println("  ✅ 通过: " + message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        boolean equal = expected == null ? actual == null : expected.equals(actual);
        assertTrue(equal, message + " [期望: " + expected + ", 实际: " + actual + "]");
    }

    private static void assertNotNull(Object obj, String message) {
        assertTrue(obj != null, message);
    }

    private static void assertNull(Object obj, String message) {
        assertTrue(obj == null, message);
    }

    private static void testNormalScene() throws IOException {
        System.out.println("\n【测试1】正常路况场景");
        BufferedImage frame = loadImage("01-normal-scene.png");
        VideoQualityAnalysisResult result = analyzer.analyze(1L, "正常路况摄像头", frame, null);

        System.out.println("  亮度: " + result.getBrightness() + " | 对比度: " + result.getContrast() +
                " | 模糊评分: " + result.getBlurScore() + " | 遮挡率: " + result.getOcclusionRatio() +
                "% | 综合评分: " + result.getOverallScore() + " | 质量等级: " + result.getQualityLevel());

        assertFalse(Boolean.TRUE.equals(result.getIsBlackScreen()), "正常画面不应黑屏");
        assertTrue(result.getBrightness().compareTo(BigDecimal.valueOf(100)) >= 0,
                "亮度应>=100");
        assertTrue(result.getBrightness().compareTo(BigDecimal.valueOf(160)) <= 0,
                "亮度应<=160");
        assertEquals(1, result.getBrightnessLevel(), "亮度等级应为正常(1)");
        assertTrue(result.getContrast().compareTo(BigDecimal.valueOf(50)) >= 0,
                "对比度应>=50");
        assertEquals(1, result.getContrastLevel(), "对比度等级应为正常(1)");
        assertTrue(result.getBlurScore().compareTo(BigDecimal.valueOf(0.65)) >= 0,
                "模糊评分应>=0.65");
        assertEquals(1, result.getBlurLevel(), "模糊等级应为正常(1)");
        assertTrue(result.getOcclusionRatio().compareTo(BigDecimal.valueOf(10)) < 0,
                "遮挡率应<10%");
        assertEquals(1, result.getOcclusionLevel(), "遮挡等级应为正常(1)");
        assertFalse(Boolean.TRUE.equals(result.getIsFrozen()), "单帧检测不应冻结");
        assertFalse(Boolean.TRUE.equals(result.getIsAbnormal()), "不应标记为异常");
        assertNull(result.getAbnormalTypes(), "异常类型应为空");
        assertTrue(result.getOverallScore().compareTo(BigDecimal.valueOf(75)) >= 0,
                "综合评分应>=75");
        assertTrue(result.getQualityLevel() <= 2, "质量等级应为优或良");
        assertNotNull(result.getDetectionDurationMs(), "检测耗时不应为空");
        assertNotNull(result.getAlgorithmVersion(), "算法版本不应为空");
    }

    private static void testBlackScreen() throws IOException {
        System.out.println("\n【测试2】黑屏场景");
        BufferedImage frame = loadImage("02-black-screen.png");
        VideoQualityAnalysisResult result = analyzer.analyze(2L, "黑屏摄像头", frame, null);

        System.out.println("  亮度: " + result.getBrightness() + " | 是否黑屏: " + result.getIsBlackScreen() +
                " | 综合评分: " + result.getOverallScore() + " | 异常类型: " + result.getAbnormalTypes());

        assertTrue(Boolean.TRUE.equals(result.getIsBlackScreen()), "应检测到黑屏");
        assertTrue(Boolean.TRUE.equals(result.getIsAbnormal()), "应标记为异常");
        assertTrue(result.getBrightness().compareTo(BigDecimal.valueOf(5)) <= 0,
                "亮度应<=5");
        assertEquals(4, result.getBrightnessLevel(), "亮度等级应为4");
        assertTrue(result.getContrast().compareTo(BigDecimal.valueOf(10)) <= 0,
                "对比度应<=10");
        assertTrue(result.getOverallScore().compareTo(BigDecimal.valueOf(20)) <= 0,
                "综合评分应<=20");
        assertEquals(5, result.getQualityLevel(), "质量等级应为严重异常(5)");
        assertNotNull(result.getAbnormalTypes(), "异常类型不应为空");
        assertTrue(result.getAbnormalTypes().contains("BLACK_SCREEN"),
                "异常类型应包含BLACK_SCREEN");
        assertTrue(result.getDescription().contains("黑屏"),
                "描述应包含黑屏信息");
    }

    private static void testLowQualityScene() throws IOException {
        System.out.println("\n【测试3】低亮+低对比+模糊场景");
        BufferedImage frame = loadImage("03-low-quality.png");
        VideoQualityAnalysisResult result = analyzer.analyze(3L, "低质量摄像头", frame, null);

        System.out.println("  亮度: " + result.getBrightness() + " | 对比度: " + result.getContrast() +
                " | 模糊评分: " + result.getBlurScore() + " | 综合评分: " + result.getOverallScore() +
                " | 异常类型: " + result.getAbnormalTypes());

        assertFalse(Boolean.TRUE.equals(result.getIsBlackScreen()), "不应判定为黑屏");
        assertTrue(Boolean.TRUE.equals(result.getIsAbnormal()), "应标记为异常");
        assertTrue(result.getBrightness().compareTo(BigDecimal.valueOf(20)) >= 0,
                "亮度应>=20");
        assertTrue(result.getBrightness().compareTo(BigDecimal.valueOf(40)) <= 0,
                "亮度应<=40");
        assertTrue(result.getBrightnessLevel() >= 2,
                "亮度等级应>=2");
        assertTrue(result.getContrast().compareTo(BigDecimal.valueOf(10)) >= 0,
                "对比度应>=10");
        assertTrue(result.getContrast().compareTo(BigDecimal.valueOf(35)) <= 0,
                "对比度应<=35");
        assertEquals(2, result.getContrastLevel(),
                "对比度等级应为偏低(2)");
        assertTrue(result.getBlurScore().compareTo(BigDecimal.valueOf(0.65)) <= 0,
                "模糊评分应<=0.65");
        assertTrue(result.getBlurLevel() >= 2,
                "模糊等级应>=2");
        assertNotNull(result.getAbnormalTypes(), "异常类型不应为空");
        assertTrue(result.getAbnormalTypes().contains("LOW_BRIGHTNESS"),
                "异常类型应包含LOW_BRIGHTNESS");
        assertTrue(result.getAbnormalTypes().contains("LOW_CONTRAST"),
                "异常类型应包含LOW_CONTRAST");
        assertTrue(result.getAbnormalTypes().contains("BLUR"),
                "异常类型应包含BLUR");
        assertTrue(result.getOverallScore().compareTo(BigDecimal.valueOf(70)) <= 0,
                "综合评分应<=70");
        assertTrue(result.getQualityLevel() >= 2,
                "质量等级应>=2");
    }

    private static void testOccludedScene() throws IOException {
        System.out.println("\n【测试4】严重遮挡场景");
        BufferedImage frame = loadImage("04-occluded.png");
        VideoQualityAnalysisResult result = analyzer.analyze(4L, "遮挡摄像头", frame, null);

        System.out.println("  遮挡率: " + result.getOcclusionRatio() + "% | 遮挡等级: " + result.getOcclusionLevel() +
                " | 综合评分: " + result.getOverallScore() + " | 异常类型: " + result.getAbnormalTypes());
        System.out.println("  遮挡区域: " + result.getOcclusionRegions());

        assertTrue(Boolean.TRUE.equals(result.getIsAbnormal()), "应标记为异常");
        assertTrue(result.getOcclusionRatio().compareTo(BigDecimal.valueOf(20)) >= 0,
                "遮挡率应>=20%");
        assertTrue(result.getOcclusionLevel() >= 2,
                "遮挡等级应>=2");
        assertNotNull(result.getOcclusionRegions(), "遮挡区域JSON不应为空");
        assertTrue(result.getOcclusionRegions().startsWith("["),
                "遮挡区域应为JSON数组格式");
        assertTrue(result.getOcclusionRegions().length() > 10,
                "遮挡区域JSON应包含有效数据");
        assertNotNull(result.getAbnormalTypes(), "异常类型不应为空");
        assertTrue(result.getAbnormalTypes().contains("OCCLUSION"),
                "异常类型应包含OCCLUSION");
        assertTrue(result.getOverallScore().compareTo(BigDecimal.valueOf(70)) <= 0,
                "综合评分应<=70");
        assertTrue(result.getQualityLevel() >= 2,
                "质量等级应>=2");
        assertTrue(result.getDescription().contains("遮挡"),
                "描述应包含遮挡信息");
    }

    private static void testFreezeScene() throws IOException {
        System.out.println("\n【测试5】冻结场景");
        BufferedImage frame1 = loadImage("05-freeze-frame1.png");
        BufferedImage frame2 = loadImage("05-freeze-frame2.png");

        Long cameraId = 5L;
        analyzer.clearFrameHistory(cameraId);

        VideoQualityAnalysisResult result1 = analyzer.analyze(cameraId, "冻结摄像头", frame1, null);
        System.out.println("  第1帧 - 帧变化率: " + result1.getFrameChangeRate() + " | 是否冻结: " + result1.getIsFrozen());
        assertFalse(Boolean.TRUE.equals(result1.getIsFrozen()), "第1帧检测不应冻结");

        VideoQualityAnalysisResult result2 = analyzer.analyze(cameraId, "冻结摄像头", frame2, frame1);
        System.out.println("  第2帧 - 帧变化率: " + result2.getFrameChangeRate() + " | 是否冻结: " + result2.getIsFrozen());
        assertTrue(result2.getFrameChangeRate().compareTo(BigDecimal.valueOf(0.02)) <= 0,
                "帧变化率应<=0.02");

        analyzer.analyze(cameraId, "冻结摄像头", frame1, frame2);
        VideoQualityAnalysisResult result4 = analyzer.analyze(cameraId, "冻结摄像头", frame2, frame1);
        System.out.println("  第4帧 - 帧变化率: " + result4.getFrameChangeRate() + " | 是否冻结: " + result4.getIsFrozen());

        assertTrue(Boolean.TRUE.equals(result4.getIsFrozen()),
                "连续多帧相同应检测到冻结");
        assertTrue(Boolean.TRUE.equals(result4.getIsAbnormal()),
                "冻结应标记为异常");
        assertNotNull(result4.getFreezeDuration(), "冻结持续时间不应为空");
        assertTrue(result4.getFreezeDuration() >= 0, "冻结持续时间应>=0");
        assertNotNull(result4.getAbnormalTypes(), "异常类型不应为空");
        assertTrue(result4.getAbnormalTypes().contains("FREEZE"),
                "异常类型应包含FREEZE");
        assertTrue(result4.getDescription().contains("冻结"),
                "描述应包含冻结信息");

        analyzer.clearFrameHistory(cameraId);
    }

    private static void testNonFreezeScene() throws IOException {
        System.out.println("\n【测试6】非冻结场景(不同帧)");
        BufferedImage normalFrame = loadImage("01-normal-scene.png");
        BufferedImage blackFrame = loadImage("02-black-screen.png");

        Long cameraId = 6L;
        analyzer.clearFrameHistory(cameraId);

        analyzer.analyze(cameraId, "变化摄像头", normalFrame, null);
        VideoQualityAnalysisResult result2 = analyzer.analyze(cameraId, "变化摄像头", blackFrame, normalFrame);

        System.out.println("  帧变化率: " + result2.getFrameChangeRate() + " | 是否冻结: " + result2.getIsFrozen());

        assertTrue(result2.getFrameChangeRate().compareTo(BigDecimal.valueOf(0.1)) > 0,
                "不同帧变化率应>0.1");
        assertFalse(Boolean.TRUE.equals(result2.getIsFrozen()),
                "不同帧不应检测为冻结");

        analyzer.clearFrameHistory(cameraId);
    }

    private static void testNullFrame() {
        System.out.println("\n【测试7】Null帧异常处理");
        VideoQualityAnalysisResult result = analyzer.analyze(99L, "离线摄像头", null, null);

        System.out.println("  亮度: " + result.getBrightness() + " | 是否黑屏: " + result.getIsBlackScreen() +
                " | 综合评分: " + result.getOverallScore());

        assertTrue(Boolean.TRUE.equals(result.getIsBlackScreen()), "Null帧应判定为黑屏");
        assertEquals(BigDecimal.ZERO, result.getBrightness(), "Null帧亮度应为0");
        assertEquals(4, result.getBrightnessLevel(), "Null帧亮度等级应为4");
        assertEquals(BigDecimal.ZERO, result.getOverallScore(), "Null帧综合评分应为0");
        assertEquals(5, result.getQualityLevel(), "Null帧质量等级应为5");
        assertTrue(Boolean.TRUE.equals(result.getIsAbnormal()), "Null帧应标记为异常");
        assertNotNull(result.getAbnormalTypes(), "异常类型不应为空");
        assertTrue(result.getAbnormalTypes().contains("BLACK_SCREEN"),
                "异常类型应包含BLACK_SCREEN");
        assertTrue(result.getDescription().contains("黑屏"),
                "描述应包含黑屏");
    }

    private static void testClearFrameHistory() throws IOException {
        System.out.println("\n【测试8】帧历史清除");
        BufferedImage frame1 = loadImage("05-freeze-frame1.png");
        BufferedImage frame2 = loadImage("05-freeze-frame2.png");

        Long cameraId = 100L;
        analyzer.clearFrameHistory(cameraId);

        for (int i = 0; i < 5; i++) {
            analyzer.analyze(cameraId, "测试摄像头", frame1, i == 0 ? null : frame2);
        }

        VideoQualityAnalysisResult frozenResult = analyzer.analyze(cameraId, "测试摄像头", frame2, frame1);
        assertTrue(Boolean.TRUE.equals(frozenResult.getIsFrozen()), "连续相同帧应检测为冻结");

        analyzer.clearFrameHistory(cameraId);

        VideoQualityAnalysisResult clearedResult = analyzer.analyze(cameraId, "测试摄像头", frame2, frame1);
        assertFalse(Boolean.TRUE.equals(clearedResult.getIsFrozen()), "清除历史后不应有冻结累积");

        System.out.println("  冻结检测后清除历史 - 验证通过");
        analyzer.clearFrameHistory(cameraId);
    }

    private static void testOutputCompleteness() throws IOException {
        System.out.println("\n【测试9】算法输出完整性验证");
        String[] testImages = {
                "01-normal-scene.png",
                "02-black-screen.png",
                "03-low-quality.png",
                "04-occluded.png",
                "05-freeze-frame1.png"
        };

        for (int i = 0; i < testImages.length; i++) {
            String imageName = testImages[i];
            BufferedImage frame = loadImage(imageName);
            VideoQualityAnalysisResult result = analyzer.analyze(
                    (long) (i + 10), "完整性测试-" + imageName, frame, null);

            assertNotNull(result, imageName + " 结果不应为空");
            assertNotNull(result.getCameraId(), imageName + " cameraId不应为空");
            assertNotNull(result.getBrightness(), imageName + " 亮度值不应为空");
            assertNotNull(result.getContrast(), imageName + " 对比度值不应为空");
            assertNotNull(result.getBlurScore(), imageName + " 模糊评分不应为空");
            assertNotNull(result.getOcclusionRatio(), imageName + " 遮挡率不应为空");
            assertNotNull(result.getOverallScore(), imageName + " 综合评分不应为空");
            assertNotNull(result.getQualityLevel(), imageName + " 质量等级不应为空");
            assertNotNull(result.getIsAbnormal(), imageName + " 异常标记不应为空");
            assertNotNull(result.getBrightnessLevel(), imageName + " 亮度等级不应为空");
            assertNotNull(result.getContrastLevel(), imageName + " 对比度等级不应为空");
            assertNotNull(result.getBlurLevel(), imageName + " 模糊等级不应为空");
            assertNotNull(result.getOcclusionLevel(), imageName + " 遮挡等级不应为空");
            assertNotNull(result.getIsBlackScreen(), imageName + " 黑屏标记不应为空");
            assertNotNull(result.getIsFrozen(), imageName + " 冻结标记不应为空");
            assertNotNull(result.getFrameChangeRate(), imageName + " 帧变化率不应为空");
            assertNotNull(result.getNoiseLevel(), imageName + " 噪声等级不应为空");
            assertNotNull(result.getColorCastLevel(), imageName + " 偏色等级不应为空");
            assertNotNull(result.getDetectionDurationMs(), imageName + " 检测耗时不应为空");
            assertNotNull(result.getAlgorithmVersion(), imageName + " 算法版本不应为空");
            assertNotNull(result.getDetectionTime(), imageName + " 检测时间不应为空");
            assertNotNull(result.getDescription(), imageName + " 描述不应为空");
        }
        System.out.println("  所有图像输出字段完整性 - 验证通过");
    }
}
