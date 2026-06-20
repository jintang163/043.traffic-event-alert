package com.traffic.alert.service;

import com.traffic.alert.config.VideoQualityConfig;
import com.traffic.alert.dto.VideoQualityAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoQualityAnalyzer {

    private final VideoQualityConfig config;

    private final ConcurrentHashMap<Long, FrameHistory> frameHistoryMap = new ConcurrentHashMap<>();

    public VideoQualityAnalysisResult analyze(Long cameraId, String cameraName, BufferedImage frame, BufferedImage previousFrame) {
        long startTime = System.currentTimeMillis();
        VideoQualityAnalysisResult result = new VideoQualityAnalysisResult();
        result.setCameraId(cameraId);
        result.setCameraName(cameraName);
        result.setDetectionTime(LocalDateTime.now());
        result.setAlgorithmVersion("v1.0.0-java-pure");

        if (frame == null) {
            result.setIsBlackScreen(true);
            result.setBrightness(BigDecimal.ZERO);
            result.setBrightnessLevel(4);
            result.setOverallScore(BigDecimal.ZERO);
            result.setQualityLevel(5);
            result.setIsAbnormal(true);
            result.setAbnormalTypes("BLACK_SCREEN");
            result.setDescription("无法获取视频帧，疑似黑屏或离线");
            return result;
        }

        int width = frame.getWidth();
        int height = frame.getHeight();
        int totalPixels = width * height;
        int[] pixels = getPixelData(frame);

        BigDecimal brightness = calculateBrightness(pixels, totalPixels);
        result.setBrightness(brightness);
        result.setBrightnessLevel(classifyBrightness(brightness));

        BigDecimal contrast = calculateContrast(pixels, totalPixels, brightness);
        result.setContrast(contrast);
        result.setContrastLevel(classifyContrast(contrast));

        BigDecimal blurScore = calculateBlurScore(frame, pixels, width, height);
        result.setBlurScore(blurScore);
        result.setBlurLevel(classifyBlur(blurScore));

        OcclusionResult occlusion = detectOcclusion(pixels, width, height, brightness);
        result.setOcclusionRatio(occlusion.ratio);
        result.setOcclusionLevel(classifyOcclusion(occlusion.ratio));
        result.setOcclusionRegions(occlusion.regionsJson);

        boolean isBlackScreen = brightness.compareTo(config.getThresholds().getBlackScreenBrightness()) <= 0;
        result.setIsBlackScreen(isBlackScreen);

        FreezeResult freeze = detectFreeze(cameraId, pixels, previousFrame, width, height, totalPixels);
        result.setIsFrozen(freeze.isFrozen);
        result.setFreezeDuration(freeze.durationSeconds);
        result.setFrameChangeRate(freeze.changeRate);

        BigDecimal noiseLevel = estimateNoiseLevel(pixels, width, height, brightness);
        result.setNoiseLevel(noiseLevel);

        int colorCastLevel = detectColorCast(frame, pixels, totalPixels);
        result.setColorCastLevel(colorCastLevel);

        List<String> abnormalTypes = new ArrayList<>();
        if (isBlackScreen) {
            abnormalTypes.add("BLACK_SCREEN");
        }
        if (result.getBrightnessLevel() == 2) {
            abnormalTypes.add("LOW_BRIGHTNESS");
        }
        if (result.getBrightnessLevel() == 3) {
            abnormalTypes.add("HIGH_BRIGHTNESS");
        }
        if (result.getContrastLevel() == 2) {
            abnormalTypes.add("LOW_CONTRAST");
        }
        if (result.getContrastLevel() == 3) {
            abnormalTypes.add("HIGH_CONTRAST");
        }
        if (result.getBlurLevel() >= 2) {
            abnormalTypes.add("BLUR");
        }
        if (result.getOcclusionLevel() >= 2) {
            abnormalTypes.add("OCCLUSION");
        }
        if (freeze.isFrozen) {
            abnormalTypes.add("FREEZE");
        }
        if (colorCastLevel >= 2) {
            abnormalTypes.add("COLOR_CAST");
        }

        BigDecimal overallScore = calculateOverallScore(
                brightness, contrast, blurScore, occlusion.ratio,
                freeze.changeRate, noiseLevel, isBlackScreen, freeze.isFrozen
        );
        result.setOverallScore(overallScore);
        result.setQualityLevel(classifyQuality(overallScore));

        boolean isAbnormal = !abnormalTypes.isEmpty();
        result.setIsAbnormal(isAbnormal);
        result.setAbnormalTypes(String.join(",", abnormalTypes));
        result.setDescription(generateDescription(result, abnormalTypes));

        int duration = (int) (System.currentTimeMillis() - startTime);
        result.setDetectionDurationMs(duration);

        return result;
    }

    public VideoQualityAnalysisResult analyzeMock(Long cameraId, String cameraName, int mockScenario) {
        VideoQualityAnalysisResult result = new VideoQualityAnalysisResult();
        result.setCameraId(cameraId);
        result.setCameraName(cameraName);
        result.setDetectionTime(LocalDateTime.now());
        result.setAlgorithmVersion("v1.0.0-mock");

        VideoQualityConfig.Thresholds t = config.getThresholds();
        List<String> abnormalTypes = new ArrayList<>();

        switch (mockScenario) {
            case 0:
                result.setBrightness(BigDecimal.valueOf(135.5));
                result.setBrightnessLevel(1);
                result.setContrast(BigDecimal.valueOf(65.8));
                result.setContrastLevel(1);
                result.setBlurScore(BigDecimal.valueOf(0.92));
                result.setBlurLevel(1);
                result.setOcclusionRatio(BigDecimal.valueOf(0.8));
                result.setOcclusionLevel(1);
                result.setIsBlackScreen(false);
                result.setIsFrozen(false);
                result.setFreezeDuration(0);
                result.setFrameChangeRate(BigDecimal.valueOf(0.85));
                result.setNoiseLevel(BigDecimal.valueOf(5.2));
                result.setColorCastLevel(1);
                result.setOverallScore(BigDecimal.valueOf(95.5));
                result.setQualityLevel(1);
                result.setIsAbnormal(false);
                result.setDescription("画面质量正常");
                break;
            case 1:
                result.setBrightness(t.getBlackScreenBrightness().subtract(BigDecimal.valueOf(2)));
                result.setBrightnessLevel(4);
                result.setContrast(BigDecimal.valueOf(5.2));
                result.setContrastLevel(2);
                result.setBlurScore(t.getSevereBlur().subtract(BigDecimal.valueOf(0.1)));
                result.setBlurLevel(3);
                result.setOcclusionRatio(BigDecimal.ZERO);
                result.setOcclusionLevel(1);
                result.setIsBlackScreen(true);
                result.setIsFrozen(false);
                result.setFrameChangeRate(BigDecimal.valueOf(0.01));
                result.setNoiseLevel(BigDecimal.valueOf(2.1));
                result.setColorCastLevel(1);
                result.setOverallScore(BigDecimal.valueOf(12.5));
                result.setQualityLevel(5);
                result.setIsAbnormal(true);
                abnormalTypes.add("BLACK_SCREEN");
                abnormalTypes.add("BLUR");
                result.setAbnormalTypes(String.join(",", abnormalTypes));
                result.setDescription("疑似黑屏，请立即排查摄像头");
                break;
            case 2:
                result.setBrightness(t.getLowBrightness().subtract(BigDecimal.valueOf(5)));
                result.setBrightnessLevel(2);
                result.setContrast(t.getLowContrast().subtract(BigDecimal.valueOf(3)));
                result.setContrastLevel(2);
                result.setBlurScore(t.getSlightBlur().subtract(BigDecimal.valueOf(0.05)));
                result.setBlurLevel(2);
                result.setOcclusionRatio(t.getSlightOcclusion().add(BigDecimal.valueOf(5)));
                result.setOcclusionLevel(2);
                result.setIsBlackScreen(false);
                result.setIsFrozen(false);
                result.setFrameChangeRate(BigDecimal.valueOf(0.78));
                result.setNoiseLevel(BigDecimal.valueOf(8.5));
                result.setColorCastLevel(1);
                result.setOverallScore(BigDecimal.valueOf(58.5));
                result.setQualityLevel(3);
                result.setIsAbnormal(true);
                abnormalTypes.add("LOW_BRIGHTNESS");
                abnormalTypes.add("LOW_CONTRAST");
                abnormalTypes.add("BLUR");
                abnormalTypes.add("OCCLUSION");
                result.setAbnormalTypes(String.join(",", abnormalTypes));
                result.setDescription("画面偏暗、对比度低，存在轻微遮挡和模糊");
                break;
            case 3:
                result.setBrightness(BigDecimal.valueOf(110.0));
                result.setBrightnessLevel(1);
                result.setContrast(BigDecimal.valueOf(58.0));
                result.setContrastLevel(1);
                result.setBlurScore(BigDecimal.valueOf(0.88));
                result.setBlurLevel(1);
                result.setOcclusionRatio(t.getSevereOcclusion().add(BigDecimal.valueOf(10)));
                result.setOcclusionLevel(3);
                result.setIsBlackScreen(false);
                result.setIsFrozen(false);
                result.setFrameChangeRate(BigDecimal.valueOf(0.82));
                result.setNoiseLevel(BigDecimal.valueOf(6.8));
                result.setColorCastLevel(1);
                result.setOverallScore(BigDecimal.valueOf(45.0));
                result.setQualityLevel(4);
                result.setIsAbnormal(true);
                abnormalTypes.add("OCCLUSION");
                result.setAbnormalTypes(String.join(",", abnormalTypes));
                result.setDescription("画面存在严重遮挡，疑似异物或镜头脏污");
                break;
            case 4:
                result.setBrightness(BigDecimal.valueOf(125.0));
                result.setBrightnessLevel(1);
                result.setContrast(BigDecimal.valueOf(60.0));
                result.setContrastLevel(1);
                result.setBlurScore(BigDecimal.valueOf(0.90));
                result.setBlurLevel(1);
                result.setOcclusionRatio(BigDecimal.ZERO);
                result.setOcclusionLevel(1);
                result.setIsBlackScreen(false);
                result.setIsFrozen(true);
                result.setFreezeDuration(180);
                result.setFrameChangeRate(t.getFreezeFrameChange());
                result.setNoiseLevel(BigDecimal.valueOf(4.5));
                result.setColorCastLevel(1);
                result.setOverallScore(BigDecimal.valueOf(35.0));
                result.setQualityLevel(4);
                result.setIsAbnormal(true);
                abnormalTypes.add("FREEZE");
                result.setAbnormalTypes(String.join(",", abnormalTypes));
                result.setDescription("画面疑似冻结超过3分钟，请检查视频流");
                break;
            default:
                result.setBrightness(BigDecimal.valueOf(130.0));
                result.setBrightnessLevel(1);
                result.setContrast(BigDecimal.valueOf(62.0));
                result.setContrastLevel(1);
                result.setBlurScore(BigDecimal.valueOf(0.88));
                result.setBlurLevel(1);
                result.setOcclusionRatio(BigDecimal.valueOf(2.0));
                result.setOcclusionLevel(1);
                result.setIsBlackScreen(false);
                result.setIsFrozen(false);
                result.setFrameChangeRate(BigDecimal.valueOf(0.80));
                result.setNoiseLevel(BigDecimal.valueOf(5.8));
                result.setColorCastLevel(1);
                result.setOverallScore(BigDecimal.valueOf(90.0));
                result.setQualityLevel(1);
                result.setIsAbnormal(false);
                result.setDescription("画面质量正常");
                break;
        }

        result.setDetectionDurationMs((int) (Math.random() * 50 + 10));
        return result;
    }

    private int[] getPixelData(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);
        return pixels;
    }

    private BigDecimal calculateBrightness(int[] pixels, int total) {
        long sum = 0;
        for (int p : pixels) {
            int r = (p >> 16) & 0xFF;
            int g = (p >> 8) & 0xFF;
            int b = p & 0xFF;
            sum += (long) (0.299 * r + 0.587 * g + 0.114 * b);
        }
        return BigDecimal.valueOf((double) sum / total).setScale(4, RoundingMode.HALF_UP);
    }

    private int classifyBrightness(BigDecimal brightness) {
        VideoQualityConfig.Thresholds t = config.getThresholds();
        if (brightness.compareTo(t.getBlackScreenBrightness()) <= 0) return 4;
        if (brightness.compareTo(t.getLowBrightness()) < 0) return 2;
        if (brightness.compareTo(t.getHighBrightness()) > 0) return 3;
        return 1;
    }

    private BigDecimal calculateContrast(int[] pixels, int total, BigDecimal avgBrightness) {
        double avg = avgBrightness.doubleValue();
        double sumSq = 0;
        for (int p : pixels) {
            int r = (p >> 16) & 0xFF;
            int g = (p >> 8) & 0xFF;
            int b = p & 0xFF;
            double lum = 0.299 * r + 0.587 * g + 0.114 * b;
            sumSq += (lum - avg) * (lum - avg);
        }
        return BigDecimal.valueOf(Math.sqrt(sumSq / total)).setScale(4, RoundingMode.HALF_UP);
    }

    private int classifyContrast(BigDecimal contrast) {
        VideoQualityConfig.Thresholds t = config.getThresholds();
        if (contrast.compareTo(t.getLowContrast()) < 0) return 2;
        if (contrast.compareTo(t.getHighContrast()) > 0) return 3;
        return 1;
    }

    private BigDecimal calculateBlurScore(BufferedImage image, int[] pixels, int width, int height) {
        int step = Math.max(1, Math.min(width, height) / 200);
        long sumEdgeStrength = 0;
        long edgePixelCount = 0;
        int threshold = 30;

        for (int y = step; y < height - step; y += step) {
            for (int x = step; x < width - step; x += step) {
                int idx = y * width + x;
                int p = pixels[idx];
                int lum = (int) (0.299 * ((p >> 16) & 0xFF) + 0.587 * ((p >> 8) & 0xFF) + 0.114 * (p & 0xFF));

                int pRight = pixels[idx + step];
                int lumRight = (int) (0.299 * ((pRight >> 16) & 0xFF) + 0.587 * ((pRight >> 8) & 0xFF) + 0.114 * (pRight & 0xFF));

                int pDown = pixels[idx + step * width];
                int lumDown = (int) (0.299 * ((pDown >> 16) & 0xFF) + 0.587 * ((pDown >> 8) & 0xFF) + 0.114 * (pDown & 0xFF));

                int gx = Math.abs(lumRight - lum);
                int gy = Math.abs(lumDown - lum);
                int edge = Math.max(gx, gy);

                sumEdgeStrength += edge;
                if (edge > threshold) {
                    edgePixelCount++;
                }
            }
        }

        if (sumEdgeStrength == 0) {
            return BigDecimal.valueOf(0.05);
        }

        int totalSamples = ((height - 2 * step) / step) * ((width - 2 * step) / step);
        double avgEdge = (double) sumEdgeStrength / totalSamples;
        double edgeDensity = totalSamples > 0 ? (double) edgePixelCount / totalSamples : 0;

        double normalizedEdge = Math.min(avgEdge / 80.0, 1.0);
        double normalizedDensity = Math.min(edgeDensity / 0.25, 1.0);
        double blurScore = 0.4 * normalizedEdge + 0.6 * normalizedDensity;
        blurScore = Math.min(1.0, Math.max(0.05, blurScore));

        return BigDecimal.valueOf(blurScore).setScale(4, RoundingMode.HALF_UP);
    }

    private int classifyBlur(BigDecimal blurScore) {
        VideoQualityConfig.Thresholds t = config.getThresholds();
        if (blurScore.compareTo(t.getSevereBlur()) <= 0) return 3;
        if (blurScore.compareTo(t.getSlightBlur()) < 0) return 2;
        return 1;
    }

    private static class OcclusionResult {
        BigDecimal ratio;
        String regionsJson;
    }

    private OcclusionResult detectOcclusion(int[] pixels, int width, int height, BigDecimal avgBrightness) {
        int gridCols = 8;
        int gridRows = 6;
        int cellW = width / gridCols;
        int cellH = height / gridRows;
        double avgB = avgBrightness.doubleValue();

        boolean[][] darkCells = new boolean[gridRows][gridCols];
        int darkCellCount = 0;
        List<int[]> darkRegions = new ArrayList<>();

        for (int r = 0; r < gridRows; r++) {
            for (int c = 0; c < gridCols; c++) {
                int startX = c * cellW;
                int startY = r * cellH;
                long cellSum = 0;
                int cellCount = 0;
                int lowVarCount = 0;
                int sampleStep = 4;

                for (int y = startY; y < startY + cellH && y < height; y += sampleStep) {
                    for (int x = startX; x < startX + cellW && x < width; x += sampleStep) {
                        int idx = y * width + x;
                        int p = pixels[idx];
                        int lum = (int) (0.299 * ((p >> 16) & 0xFF) + 0.587 * ((p >> 8) & 0xFF) + 0.114 * (p & 0xFF));
                        cellSum += lum;
                        cellCount++;
                        if (Math.abs(lum - avgB) < 15) {
                            lowVarCount++;
                        }
                    }
                }

                double cellAvg = cellCount > 0 ? (double) cellSum / cellCount : 0;
                double lowVarRatio = cellCount > 0 ? (double) lowVarCount / cellCount : 0;

                boolean isDark = (cellAvg < avgB * 0.5 && lowVarRatio > 0.6)
                        || (cellAvg < 30 && lowVarRatio > 0.5);
                darkCells[r][c] = isDark;
                if (isDark) {
                    darkCellCount++;
                    darkRegions.add(new int[]{startX, startY, cellW, cellH});
                }
            }
        }

        removeEdgeIsolatedCells(darkCells);

        int totalCells = gridCols * gridRows;
        BigDecimal ratio = BigDecimal.valueOf((double) darkCellCount / totalCells * 100)
                .setScale(2, RoundingMode.HALF_UP);

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < darkRegions.size(); i++) {
            int[] reg = darkRegions.get(i);
            sb.append(String.format("{\"x\":%d,\"y\":%d,\"w\":%d,\"h\":%d}", reg[0], reg[1], reg[2], reg[3]));
            if (i < darkRegions.size() - 1) sb.append(",");
        }
        sb.append("]");

        OcclusionResult result = new OcclusionResult();
        result.ratio = ratio;
        result.regionsJson = sb.toString();
        return result;
    }

    private void removeEdgeIsolatedCells(boolean[][] darkCells) {
        int rows = darkCells.length;
        int cols = darkCells[0].length;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!darkCells[r][c]) continue;
                int neighbors = 0;
                if (r > 0 && darkCells[r - 1][c]) neighbors++;
                if (r < rows - 1 && darkCells[r + 1][c]) neighbors++;
                if (c > 0 && darkCells[r][c - 1]) neighbors++;
                if (c < cols - 1 && darkCells[r][c + 1]) neighbors++;
                if (neighbors == 0) {
                    darkCells[r][c] = false;
                }
            }
        }
    }

    private int classifyOcclusion(BigDecimal ratio) {
        VideoQualityConfig.Thresholds t = config.getThresholds();
        if (ratio.compareTo(t.getSevereOcclusion()) >= 0) return 3;
        if (ratio.compareTo(t.getSlightOcclusion()) >= 0) return 2;
        return 1;
    }

    private static class FreezeResult {
        boolean isFrozen;
        int durationSeconds;
        BigDecimal changeRate;
    }

    private FreezeResult detectFreeze(Long cameraId, int[] currentPixels, BufferedImage previousFrame,
                                       int width, int height, int totalPixels) {
        FreezeResult result = new FreezeResult();
        result.isFrozen = false;
        result.durationSeconds = 0;

        if (previousFrame == null) {
            result.changeRate = BigDecimal.ONE;
            FrameHistory history = frameHistoryMap.computeIfAbsent(cameraId, k -> new FrameHistory());
            history.lastPixels = currentPixels.clone();
            history.lastCheckTime = System.currentTimeMillis();
            history.consecutiveFreezeCount = 0;
            return result;
        }

        int[] prevPixels = getPixelData(previousFrame);
        int diffCount = 0;
        int sampleStep = Math.max(1, totalPixels / 50000);

        for (int i = 0; i < totalPixels; i += sampleStep) {
            int p1 = currentPixels[i];
            int p2 = prevPixels[i];
            int l1 = (int) (0.299 * ((p1 >> 16) & 0xFF) + 0.587 * ((p1 >> 8) & 0xFF) + 0.114 * (p1 & 0xFF));
            int l2 = (int) (0.299 * ((p2 >> 16) & 0xFF) + 0.587 * ((p2 >> 8) & 0xFF) + 0.114 * (p2 & 0xFF));
            if (Math.abs(l1 - l2) > 5) {
                diffCount++;
            }
        }

        int samples = (totalPixels + sampleStep - 1) / sampleStep;
        double changeRate = samples > 0 ? (double) diffCount / samples : 1.0;
        result.changeRate = BigDecimal.valueOf(changeRate).setScale(4, RoundingMode.HALF_UP);

        FrameHistory history = frameHistoryMap.computeIfAbsent(cameraId, k -> new FrameHistory());
        long now = System.currentTimeMillis();
        VideoQualityConfig.Thresholds t = config.getThresholds();

        if (changeRate < t.getFreezeFrameChange().doubleValue()) {
            history.consecutiveFreezeCount++;
            if (history.consecutiveFreezeCount == 1) {
                history.freezeStartTime = now;
            }
            if (history.consecutiveFreezeCount >= t.getFreezeMinFrames()) {
                result.isFrozen = true;
                result.durationSeconds = (int) ((now - history.freezeStartTime) / 1000);
            }
        } else {
            history.consecutiveFreezeCount = 0;
            history.freezeStartTime = 0;
        }

        history.lastPixels = currentPixels.clone();
        history.lastCheckTime = now;
        return result;
    }

    private BigDecimal estimateNoiseLevel(int[] pixels, int width, int height, BigDecimal avgBrightness) {
        int step = 3;
        double totalVar = 0;
        int count = 0;

        for (int y = step; y < height - step; y += step * 2) {
            for (int x = step; x < width - step; x += step * 2) {
                int idx = y * width + x;
                int p = pixels[idx];
                int center = (int) (0.299 * ((p >> 16) & 0xFF) + 0.587 * ((p >> 8) & 0xFF) + 0.114 * (p & 0xFF));

                long sumNeighbors = 0;
                int n = 0;
                for (int dy = -step; dy <= step; dy += step) {
                    for (int dx = -step; dx <= step; dx += step) {
                        if (dx == 0 && dy == 0) continue;
                        int nIdx = (y + dy) * width + (x + dx);
                        int np = pixels[nIdx];
                        sumNeighbors += (int) (0.299 * ((np >> 16) & 0xFF) + 0.587 * ((np >> 8) & 0xFF) + 0.114 * (np & 0xFF));
                        n++;
                    }
                }
                double avgN = n > 0 ? (double) sumNeighbors / n : center;
                totalVar += Math.abs(center - avgN);
                count++;
            }
        }

        double noise = count > 0 ? totalVar / count : 0;
        return BigDecimal.valueOf(noise).setScale(4, RoundingMode.HALF_UP);
    }

    private int detectColorCast(BufferedImage image, int[] pixels, int total) {
        long sumR = 0, sumG = 0, sumB = 0;
        int sampleStep = Math.max(1, total / 50000);
        int count = 0;

        for (int i = 0; i < total; i += sampleStep) {
            int p = pixels[i];
            sumR += (p >> 16) & 0xFF;
            sumG += (p >> 8) & 0xFF;
            sumB += p & 0xFF;
            count++;
        }

        if (count == 0) return 1;

        double avgR = (double) sumR / count;
        double avgG = (double) sumG / count;
        double avgB = (double) sumB / count;
        double avgAll = (avgR + avgG + avgB) / 3.0;

        double devR = Math.abs(avgR - avgAll) / 255.0;
        double devG = Math.abs(avgG - avgAll) / 255.0;
        double devB = Math.abs(avgB - avgAll) / 255.0;
        double maxDev = Math.max(devR, Math.max(devG, devB));

        VideoQualityConfig.Thresholds t = config.getThresholds();
        if (maxDev >= t.getSevereColorCast().doubleValue()) return 3;
        if (maxDev >= t.getSlightColorCast().doubleValue()) return 2;
        return 1;
    }

    private BigDecimal calculateOverallScore(BigDecimal brightness, BigDecimal contrast, BigDecimal blurScore,
                                              BigDecimal occlusionRatio, BigDecimal frameChangeRate,
                                              BigDecimal noiseLevel, boolean isBlackScreen, boolean isFrozen) {
        if (isBlackScreen) return BigDecimal.valueOf(5).setScale(2, RoundingMode.HALF_UP);
        if (isFrozen) return BigDecimal.valueOf(20).setScale(2, RoundingMode.HALF_UP);

        VideoQualityConfig.Scoring s = config.getScoring();

        double bScore = 100.0;
        double bd = brightness.doubleValue();
        VideoQualityConfig.Thresholds t = config.getThresholds();
        if (bd < t.getLowBrightness().doubleValue()) {
            bScore = Math.max(0, 100 * (bd / t.getLowBrightness().doubleValue()));
        } else if (bd > t.getHighBrightness().doubleValue()) {
            bScore = Math.max(0, 100 * ((255 - bd) / (255 - t.getHighBrightness().doubleValue())));
        }

        double cScore = 100.0;
        double cd = contrast.doubleValue();
        if (cd < t.getLowContrast().doubleValue()) {
            cScore = Math.max(0, 100 * (cd / t.getLowContrast().doubleValue()));
        } else if (cd > t.getHighContrast().doubleValue()) {
            cScore = Math.max(0, 100 * ((127 - cd) / (127 - t.getHighContrast().doubleValue())));
        }

        double blurS = blurScore.doubleValue() * 100;
        double occS = Math.max(0, 100 - occlusionRatio.doubleValue() * 2.5);
        double freezeS = Math.min(100, frameChangeRate.doubleValue() * 120);
        double noiseS = Math.max(0, 100 - noiseLevel.doubleValue() * 4);

        int tw = s.getBrightnessWeight() + s.getContrastWeight() + s.getBlurWeight()
                + s.getOcclusionWeight() + s.getFreezeWeight() + s.getNoiseWeight();

        double score = (bScore * s.getBrightnessWeight()
                + cScore * s.getContrastWeight()
                + blurS * s.getBlurWeight()
                + occS * s.getOcclusionWeight()
                + freezeS * s.getFreezeWeight()
                + noiseS * s.getNoiseWeight()) / tw;

        score = Math.max(0, Math.min(100, score));
        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }

    private int classifyQuality(BigDecimal overallScore) {
        VideoQualityConfig.Scoring s = config.getScoring();
        double score = overallScore.doubleValue();
        if (score >= s.getExcellentMin()) return 1;
        if (score >= s.getGoodMin()) return 2;
        if (score >= s.getMediumMin()) return 3;
        if (score >= s.getPoorMin()) return 4;
        return 5;
    }

    private String generateDescription(VideoQualityAnalysisResult result, List<String> abnormalTypes) {
        if (abnormalTypes.isEmpty()) {
            return "画面质量正常，综合评分: " + result.getOverallScore();
        }
        StringBuilder sb = new StringBuilder();
        for (String type : abnormalTypes) {
            switch (type) {
                case "BLACK_SCREEN":
                    sb.append("黑屏; ");
                    break;
                case "LOW_BRIGHTNESS":
                    sb.append("亮度偏低; ");
                    break;
                case "HIGH_BRIGHTNESS":
                    sb.append("亮度偏高; ");
                    break;
                case "LOW_CONTRAST":
                    sb.append("对比度低; ");
                    break;
                case "HIGH_CONTRAST":
                    sb.append("对比度高; ");
                    break;
                case "BLUR":
                    sb.append("画面模糊; ");
                    break;
                case "OCCLUSION":
                    sb.append("画面遮挡(").append(result.getOcclusionRatio()).append("%); ");
                    break;
                case "FREEZE":
                    sb.append("画面冻结(").append(result.getFreezeDuration()).append("s); ");
                    break;
                case "COLOR_CAST":
                    sb.append("画面偏色; ");
                    break;
            }
        }
        sb.append("综合评分: ").append(result.getOverallScore());
        return sb.toString();
    }

    private static class FrameHistory {
        int[] lastPixels;
        long lastCheckTime;
        int consecutiveFreezeCount;
        long freezeStartTime;
    }

    public void clearFrameHistory(Long cameraId) {
        frameHistoryMap.remove(cameraId);
    }
}
