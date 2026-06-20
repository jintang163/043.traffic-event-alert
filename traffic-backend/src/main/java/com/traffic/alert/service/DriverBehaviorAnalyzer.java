package com.traffic.alert.service;

import com.traffic.alert.config.DriverBehaviorConfig;
import com.traffic.alert.dto.DriverBehaviorAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverBehaviorAnalyzer {

    private final DriverBehaviorConfig config;

    private final ConcurrentHashMap<Long, FrameHistory> frameHistoryMap = new ConcurrentHashMap<>();

    public DriverBehaviorAnalysisResult analyze(Long cameraId, String cameraName, BufferedImage frame, BufferedImage previousFrame) {
        long startTime = System.currentTimeMillis();
        DriverBehaviorAnalysisResult result = new DriverBehaviorAnalysisResult();
        result.setCameraId(cameraId);
        result.setCameraName(cameraName);
        result.setDetectionTime(LocalDateTime.now());
        result.setAlgorithmVersion("v1.0.0-java-pure");

        if (frame == null) {
            result.setIsAbnormal(true);
            result.setAbnormalTypes("NO_FRAME");
            result.setOverallScore(BigDecimal.ZERO);
            result.setBehaviorLevel(5);
            result.setDescription("无法获取视频帧，车内摄像头可能离线");
            result.setDetectionDurationMs((int) (System.currentTimeMillis() - startTime));
            return result;
        }

        int width = frame.getWidth();
        int height = frame.getHeight();
        int[] pixels = getPixelData(frame);

        BigDecimal phoneConfidence = detectPhoneCall(pixels, width, height);
        result.setPhoneCallConfidence(phoneConfidence);
        result.setIsPhoneCall(phoneConfidence.compareTo(config.getThresholds().getPhoneCallConfidence()) >= 0);

        YawningResult yawning = detectYawning(pixels, width, height);
        result.setIsYawning(yawning.isYawning);
        result.setYawningConfidence(yawning.confidence);
        result.setMouthOpenRatio(yawning.mouthOpenRatio);

        FatigueResult fatigue = detectFatigue(cameraId, pixels, width, height);
        result.setIsFatigued(fatigue.isFatigued);
        result.setFatigueConfidence(fatigue.confidence);
        result.setEyeAspectRatio(fatigue.eyeAspectRatio);
        result.setPerclosScore(fatigue.perclosScore);

        DistractionResult distraction = detectDistraction(pixels, width, height);
        result.setIsDistracted(distraction.isDistracted);
        result.setDistractionConfidence(distraction.confidence);
        result.setHeadPoseYaw(distraction.headYaw);
        result.setHeadPosePitch(distraction.headPitch);

        List<String> abnormalTypes = new ArrayList<>();
        if (Boolean.TRUE.equals(result.getIsPhoneCall())) {
            abnormalTypes.add("PHONE_CALL");
        }
        if (Boolean.TRUE.equals(result.getIsYawning())) {
            abnormalTypes.add("YAWNING");
        }
        if (Boolean.TRUE.equals(result.getIsFatigued())) {
            abnormalTypes.add("FATIGUE");
        }
        if (Boolean.TRUE.equals(result.getIsDistracted())) {
            abnormalTypes.add("DISTRACTION");
        }

        BigDecimal overallScore = calculateOverallScore(
                phoneConfidence, yawning.confidence,
                fatigue.confidence, distraction.confidence
        );
        result.setOverallScore(overallScore);
        result.setBehaviorLevel(classifyBehaviorLevel(overallScore, abnormalTypes));

        boolean isAbnormal = !abnormalTypes.isEmpty();
        result.setIsAbnormal(isAbnormal);
        result.setAbnormalTypes(String.join(",", abnormalTypes));
        result.setDescription(generateDescription(result, abnormalTypes));

        int duration = (int) (System.currentTimeMillis() - startTime);
        result.setDetectionDurationMs(duration);

        return result;
    }

    public DriverBehaviorAnalysisResult analyzeMock(Long cameraId, String cameraName, int mockScenario) {
        DriverBehaviorAnalysisResult result = new DriverBehaviorAnalysisResult();
        result.setCameraId(cameraId);
        result.setCameraName(cameraName);
        result.setDetectionTime(LocalDateTime.now());
        result.setAlgorithmVersion("v1.0.0-mock");

        DriverBehaviorConfig.Thresholds t = config.getThresholds();
        List<String> abnormalTypes = new ArrayList<>();

        switch (mockScenario) {
            case 0:
                result.setIsPhoneCall(false);
                result.setPhoneCallConfidence(BigDecimal.valueOf(5.50));
                result.setIsYawning(false);
                result.setYawningConfidence(BigDecimal.valueOf(3.20));
                result.setMouthOpenRatio(BigDecimal.valueOf(0.15));
                result.setIsFatigued(false);
                result.setFatigueConfidence(BigDecimal.valueOf(8.50));
                result.setEyeAspectRatio(BigDecimal.valueOf(0.28));
                result.setPerclosScore(BigDecimal.valueOf(12.50));
                result.setIsDistracted(false);
                result.setDistractionConfidence(BigDecimal.valueOf(10.50));
                result.setHeadPoseYaw(BigDecimal.valueOf(2.5));
                result.setHeadPosePitch(BigDecimal.valueOf(1.2));
                result.setOverallScore(BigDecimal.valueOf(92.50));
                result.setBehaviorLevel(1);
                result.setIsAbnormal(false);
                result.setDescription("驾驶状态良好，注意力集中");
                break;
            case 1:
                result.setIsPhoneCall(true);
                result.setPhoneCallConfidence(t.getPhoneCallConfidence().add(BigDecimal.valueOf(15.5)));
                result.setIsYawning(false);
                result.setYawningConfidence(BigDecimal.valueOf(5.20));
                result.setMouthOpenRatio(BigDecimal.valueOf(0.18));
                result.setIsFatigued(false);
                result.setFatigueConfidence(BigDecimal.valueOf(15.50));
                result.setEyeAspectRatio(BigDecimal.valueOf(0.26));
                result.setPerclosScore(BigDecimal.valueOf(18.50));
                result.setIsDistracted(true);
                result.setDistractionConfidence(t.getDistractionConfidence().add(BigDecimal.valueOf(13.5)));
                result.setHeadPoseYaw(BigDecimal.valueOf(25.5));
                result.setHeadPosePitch(BigDecimal.valueOf(3.2));
                result.setOverallScore(BigDecimal.valueOf(35.50));
                result.setBehaviorLevel(4);
                result.setIsAbnormal(true);
                abnormalTypes.add("PHONE_CALL");
                abnormalTypes.add("DISTRACTION");
                result.setAbnormalTypes(String.join(",", abnormalTypes));
                result.setDescription("检测到驾驶员正在使用手机，存在分心驾驶风险");
                break;
            case 2:
                result.setIsPhoneCall(false);
                result.setPhoneCallConfidence(BigDecimal.valueOf(8.50));
                result.setIsYawning(true);
                result.setYawningConfidence(t.getYawningConfidence().add(BigDecimal.valueOf(12.5)));
                result.setMouthOpenRatio(BigDecimal.valueOf(0.65));
                result.setIsFatigued(true);
                result.setFatigueConfidence(t.getFatigueConfidence().add(BigDecimal.valueOf(10.5)));
                result.setEyeAspectRatio(BigDecimal.valueOf(0.15));
                result.setPerclosScore(BigDecimal.valueOf(68.50));
                result.setIsDistracted(false);
                result.setDistractionConfidence(BigDecimal.valueOf(12.50));
                result.setHeadPoseYaw(BigDecimal.valueOf(3.5));
                result.setHeadPosePitch(BigDecimal.valueOf(15.5));
                result.setOverallScore(BigDecimal.valueOf(28.50));
                result.setBehaviorLevel(5);
                result.setIsAbnormal(true);
                abnormalTypes.add("YAWNING");
                abnormalTypes.add("FATIGUE");
                result.setAbnormalTypes(String.join(",", abnormalTypes));
                result.setDescription("检测到驾驶员频繁打哈欠，眼睛闭合时间过长，疑似疲劳驾驶");
                break;
            case 3:
                result.setIsPhoneCall(false);
                result.setPhoneCallConfidence(BigDecimal.valueOf(12.50));
                result.setIsYawning(false);
                result.setYawningConfidence(BigDecimal.valueOf(8.50));
                result.setMouthOpenRatio(BigDecimal.valueOf(0.22));
                result.setIsFatigued(false);
                result.setFatigueConfidence(BigDecimal.valueOf(25.50));
                result.setEyeAspectRatio(BigDecimal.valueOf(0.24));
                result.setPerclosScore(BigDecimal.valueOf(28.50));
                result.setIsDistracted(true);
                result.setDistractionConfidence(t.getDistractionConfidence().add(BigDecimal.valueOf(0.5)));
                result.setHeadPoseYaw(BigDecimal.valueOf(35.5));
                result.setHeadPosePitch(BigDecimal.valueOf(8.5));
                result.setOverallScore(BigDecimal.valueOf(58.50));
                result.setBehaviorLevel(3);
                result.setIsAbnormal(true);
                abnormalTypes.add("DISTRACTION");
                result.setAbnormalTypes(String.join(",", abnormalTypes));
                result.setDescription("检测到驾驶员头部频繁偏离正常驾驶姿态，存在分心风险");
                break;
            case 4:
                result.setIsPhoneCall(true);
                result.setPhoneCallConfidence(t.getPhoneCallConfidence().add(BigDecimal.valueOf(5.5)));
                result.setIsYawning(true);
                result.setYawningConfidence(t.getYawningConfidence().add(BigDecimal.valueOf(8.5)));
                result.setMouthOpenRatio(BigDecimal.valueOf(0.55));
                result.setIsFatigued(true);
                result.setFatigueConfidence(t.getFatigueConfidence().add(BigDecimal.valueOf(15.5)));
                result.setEyeAspectRatio(BigDecimal.valueOf(0.12));
                result.setPerclosScore(BigDecimal.valueOf(78.50));
                result.setIsDistracted(true);
                result.setDistractionConfidence(t.getDistractionConfidence().add(BigDecimal.valueOf(20.5)));
                result.setHeadPoseYaw(BigDecimal.valueOf(45.5));
                result.setHeadPosePitch(BigDecimal.valueOf(18.5));
                result.setOverallScore(BigDecimal.valueOf(15.50));
                result.setBehaviorLevel(5);
                result.setIsAbnormal(true);
                abnormalTypes.add("PHONE_CALL");
                abnormalTypes.add("YAWNING");
                abnormalTypes.add("FATIGUE");
                abnormalTypes.add("DISTRACTION");
                result.setAbnormalTypes(String.join(",", abnormalTypes));
                result.setDescription("严重警告：驾驶员同时存在打电话、打哈欠、疲劳和分心驾驶，立即停车休息！");
                break;
            default:
                result.setIsPhoneCall(false);
                result.setPhoneCallConfidence(BigDecimal.valueOf(8.0));
                result.setIsYawning(false);
                result.setYawningConfidence(BigDecimal.valueOf(5.0));
                result.setMouthOpenRatio(BigDecimal.valueOf(0.20));
                result.setIsFatigued(false);
                result.setFatigueConfidence(BigDecimal.valueOf(12.0));
                result.setEyeAspectRatio(BigDecimal.valueOf(0.25));
                result.setPerclosScore(BigDecimal.valueOf(15.0));
                result.setIsDistracted(false);
                result.setDistractionConfidence(BigDecimal.valueOf(15.0));
                result.setHeadPoseYaw(BigDecimal.valueOf(5.0));
                result.setHeadPosePitch(BigDecimal.valueOf(3.0));
                result.setOverallScore(BigDecimal.valueOf(85.0));
                result.setBehaviorLevel(2);
                result.setIsAbnormal(false);
                result.setDescription("驾驶状态良好");
                break;
        }

        result.setDetectionDurationMs((int) (System.currentTimeMillis() - System.currentTimeMillis()));
        return result;
    }

    public void clearFrameHistory(Long cameraId) {
        frameHistoryMap.remove(cameraId);
    }

    public void clearAllFrameHistory() {
        frameHistoryMap.clear();
    }

    private int[] getPixelData(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);
        return pixels;
    }

    private BigDecimal detectPhoneCall(int[] pixels, int width, int height) {
        int totalPixels = pixels.length;
        int skinPixelCount = 0;
        int leftEarSkinCount = 0;
        int rightEarSkinCount = 0;
        int earRegionYStart = height / 4;
        int earRegionYEnd = height / 2;
        int leftEarXEnd = width / 4;
        int rightEarXStart = width * 3 / 4;

        for (int i = 0; i < totalPixels; i++) {
            int x = i % width;
            int y = i / width;

            int pixel = pixels[i];
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;

            if (isSkinColor(r, g, b)) {
                skinPixelCount++;
                if (y >= earRegionYStart && y <= earRegionYEnd) {
                    if (x <= leftEarXEnd) {
                        leftEarSkinCount++;
                    } else if (x >= rightEarXStart) {
                        rightEarSkinCount++;
                    }
                }
            }
        }

        int earSkinCount = Math.max(leftEarSkinCount, rightEarSkinCount);
        int earRegionPixels = (earRegionYEnd - earRegionYStart) * (width / 4);

        if (earRegionPixels == 0 || skinPixelCount < totalPixels * 0.05) {
            return BigDecimal.ZERO;
        }

        BigDecimal earSkinRatio = BigDecimal.valueOf(earSkinCount)
                .divide(BigDecimal.valueOf(earRegionPixels), 4, RoundingMode.HALF_UP);
        BigDecimal skinRatio = BigDecimal.valueOf(skinPixelCount)
                .divide(BigDecimal.valueOf(totalPixels), 4, RoundingMode.HALF_UP);

        double earWeight = Math.min(1.0, earSkinRatio.doubleValue() * 4.0);
        double skinWeight = Math.min(1.0, skinRatio.doubleValue() * 3.0);
        double confidence = (earWeight * 0.7 + skinWeight * 0.3) * 100;

        confidence = Math.max(0, Math.min(100, confidence));
        return BigDecimal.valueOf(confidence).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isSkinColor(int r, int g, int b) {
        if (r > 95 && g > 40 && b > 20 &&
                r > g && r > b &&
                Math.abs(r - g) > 15) {
            double max = Math.max(r, Math.max(g, b));
            double min = Math.min(r, Math.min(g, b));
            if (max - min > 15) {
                int y = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                int cr = (int) (r - 0.4187 * g - 0.0813 * b + 128);
                int cb = (int) (-0.1687 * r - 0.3313 * g + 0.5 * b + 128);
                return cr >= 133 && cr <= 173 && cb >= 77 && cb <= 127 && y >= 60;
            }
        }
        return false;
    }

    private YawningResult detectYawning(int[] pixels, int width, int height) {
        YawningResult result = new YawningResult();
        int mouthRegionYStart = height * 5 / 8;
        int mouthRegionYEnd = height * 7 / 8;
        int mouthRegionXStart = width / 4;
        int mouthRegionXEnd = width * 3 / 4;

        int mouthWidth = mouthRegionXEnd - mouthRegionXStart;
        int mouthHeight = mouthRegionYEnd - mouthRegionYStart;

        if (mouthWidth <= 0 || mouthHeight <= 0) {
            result.confidence = BigDecimal.ZERO;
            result.mouthOpenRatio = BigDecimal.ZERO;
            result.isYawning = false;
            return result;
        }

        int[] verticalProjection = new int[mouthHeight];
        int[] horizontalProjection = new int[mouthWidth];
        int redPixels = 0;
        int darkPixels = 0;
        int totalMouthPixels = 0;

        for (int y = mouthRegionYStart; y < mouthRegionYEnd; y++) {
            for (int x = mouthRegionXStart; x < mouthRegionXEnd; x++) {
                int idx = y * width + x;
                int pixel = pixels[idx];
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                totalMouthPixels++;
                int localY = y - mouthRegionYStart;
                int localX = x - mouthRegionXStart;

                int brightness = (int) (0.299 * r + 0.587 * g + 0.114 * b);

                if (r > 100 && g < 80 && b < 80 && r > g + 20 && r > b + 20) {
                    redPixels++;
                    verticalProjection[localY]++;
                    horizontalProjection[localX]++;
                }

                if (brightness < 60) {
                    darkPixels++;
                }
            }
        }

        if (totalMouthPixels == 0) {
            result.confidence = BigDecimal.ZERO;
            result.mouthOpenRatio = BigDecimal.ZERO;
            result.isYawning = false;
            return result;
        }

        BigDecimal redRatio = BigDecimal.valueOf(redPixels)
                .divide(BigDecimal.valueOf(totalMouthPixels), 4, RoundingMode.HALF_UP);
        BigDecimal darkRatio = BigDecimal.valueOf(darkPixels)
                .divide(BigDecimal.valueOf(totalMouthPixels), 4, RoundingMode.HALF_UP);

        int mouthTop = -1, mouthBottom = -1;
        for (int i = 0; i < verticalProjection.length; i++) {
            if (verticalProjection[i] > mouthWidth * 0.1) {
                if (mouthTop == -1) mouthTop = i;
                mouthBottom = i;
            }
        }

        int mouthLeft = -1, mouthRight = -1;
        for (int i = 0; i < horizontalProjection.length; i++) {
            if (horizontalProjection[i] > mouthHeight * 0.1) {
                if (mouthLeft == -1) mouthLeft = i;
                mouthRight = i;
            }
        }

        if (mouthTop == -1 || mouthBottom == -1 || mouthLeft == -1 || mouthRight == -1) {
            result.mouthOpenRatio = BigDecimal.ZERO;
            double confidence = (redRatio.doubleValue() * 0.5 + darkRatio.doubleValue() * 0.3) * 100;
            result.confidence = BigDecimal.valueOf(Math.max(0, Math.min(100, confidence)))
                    .setScale(2, RoundingMode.HALF_UP);
            result.isYawning = false;
            return result;
        }

        int detectedMouthHeight = mouthBottom - mouthTop;
        int detectedMouthWidth = mouthRight - mouthLeft;

        BigDecimal openRatio = BigDecimal.ZERO;
        if (detectedMouthWidth > 0) {
            openRatio = BigDecimal.valueOf(detectedMouthHeight)
                    .divide(BigDecimal.valueOf(detectedMouthWidth), 4, RoundingMode.HALF_UP);
        }
        result.mouthOpenRatio = openRatio;

        double openScore = Math.min(1.0, openRatio.doubleValue() * 2.5);
        double redScore = Math.min(1.0, redRatio.doubleValue() * 8.0);
        double darkScore = Math.min(1.0, darkRatio.doubleValue() * 2.0);

        double confidence = (openScore * 0.5 + redScore * 0.3 + darkScore * 0.2) * 100;
        confidence = Math.max(0, Math.min(100, confidence));
        result.confidence = BigDecimal.valueOf(confidence).setScale(2, RoundingMode.HALF_UP);

        result.isYawning = result.confidence.compareTo(config.getThresholds().getYawningConfidence()) >= 0
                || openRatio.compareTo(config.getThresholds().getMouthOpenRatio()) >= 0;

        return result;
    }

    private FatigueResult detectFatigue(Long cameraId, int[] pixels, int width, int height) {
        FatigueResult result = new FatigueResult();
        int eyeRegionYStart = height / 3;
        int eyeRegionYEnd = height / 2;

        int eyeRegionHeight = eyeRegionYEnd - eyeRegionYStart;
        if (eyeRegionHeight <= 0 || width <= 0) {
            result.eyeAspectRatio = BigDecimal.valueOf(0.30);
            result.perclosScore = BigDecimal.ZERO;
            result.confidence = BigDecimal.ZERO;
            result.isFatigued = false;
            return result;
        }

        int[] horizontalProjection = new int[width];
        int[] verticalProjection = new int[eyeRegionHeight];
        int darkPixels = 0;
        int totalEyePixels = 0;

        for (int y = eyeRegionYStart; y < eyeRegionYEnd; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                int pixel = pixels[idx];
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                totalEyePixels++;
                int brightness = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                int localY = y - eyeRegionYStart;

                if (brightness < 50) {
                    darkPixels++;
                    horizontalProjection[x]++;
                    verticalProjection[localY]++;
                }
            }
        }

        if (totalEyePixels == 0) {
            result.eyeAspectRatio = BigDecimal.valueOf(0.30);
            result.perclosScore = BigDecimal.ZERO;
            result.confidence = BigDecimal.ZERO;
            result.isFatigued = false;
            return result;
        }

        int eyeLeft = -1, eyeRight = -1;
        for (int i = 0; i < horizontalProjection.length; i++) {
            if (horizontalProjection[i] > eyeRegionHeight * 0.15) {
                if (eyeLeft == -1) eyeLeft = i;
                eyeRight = i;
            }
        }

        int eyeTop = -1, eyeBottom = -1;
        for (int i = 0; i < verticalProjection.length; i++) {
            if (verticalProjection[i] > width * 0.05) {
                if (eyeTop == -1) eyeTop = i;
                eyeBottom = i;
            }
        }

        BigDecimal eyeAspectRatio;
        if (eyeLeft == -1 || eyeRight == -1 || eyeTop == -1 || eyeBottom == -1) {
            eyeAspectRatio = BigDecimal.valueOf(0.30);
        } else {
            int eyeWidth = eyeRight - eyeLeft;
            int eyeHeight = eyeBottom - eyeTop;
            if (eyeWidth > 0) {
                eyeAspectRatio = BigDecimal.valueOf(eyeHeight)
                        .divide(BigDecimal.valueOf(eyeWidth), 4, RoundingMode.HALF_UP);
            } else {
                eyeAspectRatio = BigDecimal.valueOf(0.30);
            }
        }
        result.eyeAspectRatio = eyeAspectRatio;

        FrameHistory history = frameHistoryMap.computeIfAbsent(cameraId, k -> new FrameHistory());
        boolean isEyeClosed = eyeAspectRatio.compareTo(config.getThresholds().getEyeAspectRatio()) <= 0;
        history.addEyeState(isEyeClosed);

        int totalFrames = history.eyeStates.size();
        int closedFrames = 0;
        for (Boolean closed : history.eyeStates) {
            if (Boolean.TRUE.equals(closed)) closedFrames++;
        }

        BigDecimal perclosScore = BigDecimal.ZERO;
        if (totalFrames > 0) {
            perclosScore = BigDecimal.valueOf(closedFrames)
                    .divide(BigDecimal.valueOf(totalFrames), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        result.perclosScore = perclosScore.setScale(2, RoundingMode.HALF_UP);

        double earScore = 1.0 - Math.min(1.0, eyeAspectRatio.doubleValue() / 0.35);
        double perclosScoreValue = Math.min(1.0, perclosScore.doubleValue() / 100.0);
        double darkRatio = (double) darkPixels / totalEyePixels;
        double darkScore = Math.min(1.0, darkRatio * 3.0);

        double confidence = (perclosScoreValue * 0.5 + earScore * 0.3 + darkScore * 0.2) * 100;
        confidence = Math.max(0, Math.min(100, confidence));
        result.confidence = BigDecimal.valueOf(confidence).setScale(2, RoundingMode.HALF_UP);

        result.isFatigued = result.confidence.compareTo(config.getThresholds().getFatigueConfidence()) >= 0
                || perclosScore.compareTo(config.getThresholds().getPerclosScore()) >= 0;

        return result;
    }

    private DistractionResult detectDistraction(int[] pixels, int width, int height) {
        DistractionResult result = new DistractionResult();
        int totalPixels = pixels.length;

        int faceCenterX = width / 2;
        int faceCenterY = height / 2;

        int[] horizontalProjection = new int[width];
        int[] verticalProjection = new int[height];
        int skinPixels = 0;

        for (int i = 0; i < totalPixels; i++) {
            int x = i % width;
            int y = i / width;
            int pixel = pixels[i];
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;

            if (isSkinColor(r, g, b)) {
                skinPixels++;
                horizontalProjection[x]++;
                verticalProjection[y]++;
            }
        }

        if (skinPixels < totalPixels * 0.03) {
            result.headYaw = BigDecimal.ZERO;
            result.headPitch = BigDecimal.ZERO;
            result.confidence = BigDecimal.ZERO;
            result.isDistracted = false;
            return result;
        }

        int faceLeft = -1, faceRight = -1;
        int skinThreshold = Math.max(5, height * 3 / 100);
        for (int i = 0; i < width; i++) {
            if (horizontalProjection[i] > skinThreshold) {
                if (faceLeft == -1) faceLeft = i;
                faceRight = i;
            }
        }

        int faceTop = -1, faceBottom = -1;
        int vThreshold = Math.max(5, width * 3 / 100);
        for (int i = 0; i < height; i++) {
            if (verticalProjection[i] > vThreshold) {
                if (faceTop == -1) faceTop = i;
                faceBottom = i;
            }
        }

        if (faceLeft == -1 || faceRight == -1 || faceTop == -1 || faceBottom == -1) {
            result.headYaw = BigDecimal.ZERO;
            result.headPitch = BigDecimal.ZERO;
            result.confidence = BigDecimal.ZERO;
            result.isDistracted = false;
            return result;
        }

        int detectedFaceCenterX = (faceLeft + faceRight) / 2;
        int detectedFaceCenterY = (faceTop + faceBottom) / 2;
        int faceWidth = faceRight - faceLeft;
        int faceHeight = faceBottom - faceTop;

        BigDecimal yawAngle = BigDecimal.ZERO;
        if (faceWidth > 0) {
            double xOffsetRatio = (double) (detectedFaceCenterX - faceCenterX) / faceWidth;
            yawAngle = BigDecimal.valueOf(xOffsetRatio * 60.0).setScale(2, RoundingMode.HALF_UP);
        }
        result.headYaw = yawAngle;

        BigDecimal pitchAngle = BigDecimal.ZERO;
        if (faceHeight > 0) {
            double yOffsetRatio = (double) (detectedFaceCenterY - faceCenterY) / faceHeight;
            pitchAngle = BigDecimal.valueOf(yOffsetRatio * 45.0).setScale(2, RoundingMode.HALF_UP);
        }
        result.headPitch = pitchAngle;

        double yawAbs = Math.abs(yawAngle.doubleValue());
        double pitchAbs = Math.abs(pitchAngle.doubleValue());

        double yawScore = Math.min(1.0, yawAbs / config.getThresholds().getHeadPoseYawThreshold().doubleValue());
        double pitchScore = Math.min(1.0, pitchAbs / config.getThresholds().getHeadPosePitchThreshold().doubleValue());
        double sizeScore = Math.min(1.0, (1.0 - (double) faceWidth / width) * 2.0);

        double confidence = (yawScore * 0.5 + pitchScore * 0.3 + sizeScore * 0.2) * 100;
        confidence = Math.max(0, Math.min(100, confidence));
        result.confidence = BigDecimal.valueOf(confidence).setScale(2, RoundingMode.HALF_UP);

        result.isDistracted = result.confidence.compareTo(config.getThresholds().getDistractionConfidence()) >= 0
                || yawAbs >= config.getThresholds().getHeadPoseYawThreshold().doubleValue()
                || pitchAbs >= config.getThresholds().getHeadPosePitchThreshold().doubleValue();

        return result;
    }

    private BigDecimal calculateOverallScore(BigDecimal phoneConfidence, BigDecimal yawningConfidence,
                                             BigDecimal fatigueConfidence, BigDecimal distractionConfidence) {
        DriverBehaviorConfig.Scoring s = config.getScoring();
        BigDecimal totalWeight = s.getPhoneCallWeight().add(s.getYawningWeight())
                .add(s.getFatigueWeight()).add(s.getDistractionWeight());

        BigDecimal phonePenalty = phoneConfidence.multiply(s.getPhoneCallWeight())
                .divide(totalWeight, 4, RoundingMode.HALF_UP);
        BigDecimal yawningPenalty = yawningConfidence.multiply(s.getYawningWeight())
                .divide(totalWeight, 4, RoundingMode.HALF_UP);
        BigDecimal fatiguePenalty = fatigueConfidence.multiply(s.getFatigueWeight())
                .divide(totalWeight, 4, RoundingMode.HALF_UP);
        BigDecimal distractionPenalty = distractionConfidence.multiply(s.getDistractionWeight())
                .divide(totalWeight, 4, RoundingMode.HALF_UP);

        BigDecimal totalPenalty = phonePenalty.add(yawningPenalty).add(fatiguePenalty).add(distractionPenalty);
        BigDecimal score = BigDecimal.valueOf(100).subtract(totalPenalty);
        score = score.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));

        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private int classifyBehaviorLevel(BigDecimal score, List<String> abnormalTypes) {
        if (abnormalTypes.contains("FATIGUE") || abnormalTypes.contains("PHONE_CALL")) {
            if (score.compareTo(BigDecimal.valueOf(40)) < 0) return 5;
            if (score.compareTo(BigDecimal.valueOf(60)) < 0) return 4;
        }

        if (score.compareTo(BigDecimal.valueOf(90)) >= 0) return 1;
        if (score.compareTo(BigDecimal.valueOf(75)) >= 0) return 2;
        if (score.compareTo(BigDecimal.valueOf(60)) >= 0) return 3;
        if (score.compareTo(BigDecimal.valueOf(40)) >= 0) return 4;
        return 5;
    }

    private String generateDescription(DriverBehaviorAnalysisResult result, List<String> abnormalTypes) {
        if (abnormalTypes.isEmpty()) {
            return "驾驶状态良好，注意力集中";
        }

        StringBuilder sb = new StringBuilder();
        List<String> descriptions = new ArrayList<>();

        if (Boolean.TRUE.equals(result.getIsPhoneCall())) {
            descriptions.add(String.format("打电话(置信度%.1f%%)", result.getPhoneCallConfidence()));
        }
        if (Boolean.TRUE.equals(result.getIsYawning())) {
            descriptions.add(String.format("打哈欠(张口率%.2f)", result.getMouthOpenRatio()));
        }
        if (Boolean.TRUE.equals(result.getIsFatigued())) {
            descriptions.add(String.format("疲劳驾驶(PERCLOS=%.1f%%)", result.getPerclosScore()));
        }
        if (Boolean.TRUE.equals(result.getIsDistracted())) {
            descriptions.add(String.format("分心驾驶(偏航%.1f°)", result.getHeadPoseYaw()));
        }

        if (descriptions.size() >= 3) {
            sb.append("严重警告：");
        }
        sb.append("检测到");
        sb.append(String.join("、", descriptions));

        if (result.getBehaviorLevel() != null && result.getBehaviorLevel() >= 4) {
            sb.append("，请立即停车休息！");
        } else {
            sb.append("，请注意安全驾驶！");
        }

        return sb.toString();
    }

    private static class YawningResult {
        boolean isYawning;
        BigDecimal confidence;
        BigDecimal mouthOpenRatio;
    }

    private static class FatigueResult {
        boolean isFatigued;
        BigDecimal confidence;
        BigDecimal eyeAspectRatio;
        BigDecimal perclosScore;
    }

    private static class DistractionResult {
        boolean isDistracted;
        BigDecimal confidence;
        BigDecimal headYaw;
        BigDecimal headPitch;
    }

    private static class FrameHistory {
        private static final int MAX_HISTORY = 30;
        private final List<Boolean> eyeStates = new ArrayList<>();

        void addEyeState(boolean isClosed) {
            eyeStates.add(isClosed);
            if (eyeStates.size() > MAX_HISTORY) {
                eyeStates.remove(0);
            }
        }
    }
}
