package com.traffic.alert.service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

public class TestDriverImageGenerator {

    private static final int WIDTH = 640;
    private static final int HEIGHT = 480;
    private static final String OUTPUT_DIR = "src/test/resources/driver-behavior-samples/";

    private static final Color SKIN_COLOR = new Color(210, 155, 120);
    private static final Color DARK_SKIN = new Color(180, 130, 100);
    private static final Color MOUTH_RED = new Color(180, 40, 35);
    private static final Color EYE_DARK = new Color(25, 25, 25);
    private static final Color BACKGROUND = new Color(80, 80, 90);

    public static void main(String[] args) throws IOException {
        createOutputDir();
        generateNormalDriver();
        generatePhoneCall();
        generateYawning();
        generateFatigued();
        generateDistractedLeft();
        generateDistractedRight();
        System.out.println("所有驾驶员行为测试图像生成完成！");
    }

    private static void createOutputDir() {
        File dir = new File(OUTPUT_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    private static void generateNormalDriver() throws IOException {
        BufferedImage image = createBaseImage();
        Graphics2D g2d = image.createGraphics();

        int faceCenterX = WIDTH / 2;
        int faceCenterY = HEIGHT / 2;
        int faceWidth = 200;
        int faceHeight = 240;

        drawFace(g2d, faceCenterX, faceCenterY, faceWidth, faceHeight);
        drawEyes(g2d, faceCenterX, faceCenterY - 30, 45, 18, false);
        drawMouth(g2d, faceCenterX, faceCenterY + 60, 50, 12, false);

        g2d.dispose();
        saveImage(image, "01-normal-driver.png");
        System.out.println("生成: 01-normal-driver.png (正常驾驶)");
    }

    private static void generatePhoneCall() throws IOException {
        BufferedImage image = createBaseImage();
        Graphics2D g2d = image.createGraphics();

        int faceCenterX = WIDTH / 2;
        int faceCenterY = HEIGHT / 2;
        int faceWidth = 200;
        int faceHeight = 240;

        drawFace(g2d, faceCenterX, faceCenterY, faceWidth, faceHeight);
        drawEyes(g2d, faceCenterX, faceCenterY - 30, 45, 18, false);
        drawMouth(g2d, faceCenterX, faceCenterY + 60, 45, 12, false);

        int earRegionYStart = HEIGHT / 4;
        int earRegionYEnd = HEIGHT / 2;
        int rightEarXStart = WIDTH * 3 / 4;

        g2d.setColor(SKIN_COLOR);
        g2d.fillOval(rightEarXStart + 10, earRegionYStart + 20, 120, earRegionYEnd - earRegionYStart - 40);

        g2d.setColor(DARK_SKIN);
        g2d.fillOval(rightEarXStart + 30, earRegionYStart + 40, 80, 60);

        g2d.dispose();
        saveImage(image, "02-phone-call.png");
        System.out.println("生成: 02-phone-call.png (打电话)");
    }

    private static void generateYawning() throws IOException {
        BufferedImage image = createBaseImage();
        Graphics2D g2d = image.createGraphics();

        int faceCenterX = WIDTH / 2;
        int faceCenterY = HEIGHT / 2;
        int faceWidth = 200;
        int faceHeight = 240;

        drawFace(g2d, faceCenterX, faceCenterY, faceWidth, faceHeight);
        drawEyes(g2d, faceCenterX, faceCenterY - 30, 45, 18, false);
        drawMouth(g2d, faceCenterX, faceCenterY + 60, 70, 60, true);

        g2d.dispose();
        saveImage(image, "03-yawning.png");
        System.out.println("生成: 03-yawning.png (打哈欠)");
    }

    private static void generateFatigued() throws IOException {
        BufferedImage image = createBaseImage();
        Graphics2D g2d = image.createGraphics();

        int faceCenterX = WIDTH / 2;
        int faceCenterY = HEIGHT / 2;
        int faceWidth = 200;
        int faceHeight = 240;

        drawFace(g2d, faceCenterX, faceCenterY, faceWidth, faceHeight);
        drawEyes(g2d, faceCenterX, faceCenterY - 30, 50, 8, true);
        drawMouth(g2d, faceCenterX, faceCenterY + 60, 40, 10, false);

        g2d.dispose();
        saveImage(image, "04-fatigued.png");
        System.out.println("生成: 04-fatigued.png (疲劳驾驶)");
    }

    private static void generateDistractedLeft() throws IOException {
        BufferedImage image = createBaseImage();
        Graphics2D g2d = image.createGraphics();

        int faceCenterX = WIDTH / 2 - 140;
        int faceCenterY = HEIGHT / 2;
        int faceWidth = 200;
        int faceHeight = 240;

        drawFace(g2d, faceCenterX, faceCenterY, faceWidth, faceHeight);
        drawEyes(g2d, faceCenterX, faceCenterY - 30, 45, 18, false);
        drawMouth(g2d, faceCenterX, faceCenterY + 60, 50, 12, false);

        g2d.dispose();
        saveImage(image, "05-distracted-left.png");
        System.out.println("生成: 05-distracted-left.png (分心驾驶-左侧)");
    }

    private static void generateDistractedRight() throws IOException {
        BufferedImage image = createBaseImage();
        Graphics2D g2d = image.createGraphics();

        int faceCenterX = WIDTH / 2 + 140;
        int faceCenterY = HEIGHT / 2;
        int faceWidth = 200;
        int faceHeight = 240;

        drawFace(g2d, faceCenterX, faceCenterY, faceWidth, faceHeight);
        drawEyes(g2d, faceCenterX, faceCenterY - 30, 45, 18, false);
        drawMouth(g2d, faceCenterX, faceCenterY + 60, 50, 12, false);

        g2d.dispose();
        saveImage(image, "06-distracted-right.png");
        System.out.println("生成: 06-distracted-right.png (分心驾驶-右侧)");
    }

    private static BufferedImage createBaseImage() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setColor(BACKGROUND);
        g2d.fillRect(0, 0, WIDTH, HEIGHT);

        Random random = new Random(456);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int variation = random.nextInt(20) - 10;
                int r = Math.max(0, Math.min(255, BACKGROUND.getRed() + variation));
                int g = Math.max(0, Math.min(255, BACKGROUND.getGreen() + variation));
                int b = Math.max(0, Math.min(255, BACKGROUND.getBlue() + variation));
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }

        g2d.dispose();
        return image;
    }

    private static void drawFace(Graphics2D g2d, int centerX, int centerY, int width, int height) {
        g2d.setColor(SKIN_COLOR);
        g2d.fillOval(centerX - width / 2, centerY - height / 2, width, height);

        Random random = new Random(123);
        for (int y = centerY - height / 2; y < centerY + height / 2; y++) {
            for (int x = centerX - width / 2; x < centerX + width / 2; x++) {
                int dx = x - centerX;
                int dy = y - centerY;
                if ((dx * dx) * 4 + (dy * dy) * 2.5 < (width * width) / 4) {
                    int variation = random.nextInt(25) - 12;
                    int r = Math.max(0, Math.min(255, SKIN_COLOR.getRed() + variation));
                    int g = Math.max(0, Math.min(255, SKIN_COLOR.getGreen() + variation));
                    int b = Math.max(0, Math.min(255, SKIN_COLOR.getBlue() + variation));
                    g2d.setColor(new Color(r, g, b));
                    g2d.fillRect(x, y, 1, 1);
                }
            }
        }

        g2d.setColor(new Color(160, 110, 80));
        g2d.fillOval(centerX - width / 2 - 10, centerY - height / 2 - 20, width + 20, height / 2 + 20);
    }

    private static void drawEyes(Graphics2D g2d, int centerX, int centerY, int eyeWidth, int eyeHeight, boolean closed) {
        int eyeSpacing = 60;

        g2d.setColor(new Color(255, 255, 255));
        g2d.fillOval(centerX - eyeSpacing - eyeWidth / 2, centerY - eyeHeight / 2, eyeWidth, eyeHeight);
        g2d.fillOval(centerX + eyeSpacing - eyeWidth / 2, centerY - eyeHeight / 2, eyeWidth, eyeHeight);

        if (!closed) {
            g2d.setColor(EYE_DARK);
            int pupilSize = Math.min(eyeWidth, eyeHeight) / 2;
            g2d.fillOval(centerX - eyeSpacing - pupilSize / 2, centerY - pupilSize / 2, pupilSize, pupilSize);
            g2d.fillOval(centerX + eyeSpacing - pupilSize / 2, centerY - pupilSize / 2, pupilSize, pupilSize);

            g2d.setColor(new Color(255, 255, 255));
            int highlightSize = pupilSize / 3;
            g2d.fillOval(centerX - eyeSpacing - pupilSize / 4, centerY - pupilSize / 2 - 2, highlightSize, highlightSize);
            g2d.fillOval(centerX + eyeSpacing - pupilSize / 4, centerY - pupilSize / 2 - 2, highlightSize, highlightSize);
        } else {
            g2d.setColor(EYE_DARK);
            g2d.fillRect(centerX - eyeSpacing - eyeWidth / 2, centerY - 2, eyeWidth, 4);
            g2d.fillRect(centerX + eyeSpacing - eyeWidth / 2, centerY - 2, eyeWidth, 4);
        }

        g2d.setColor(new Color(100, 70, 50));
        g2d.fillRect(centerX - eyeSpacing - eyeWidth / 2 - 5, centerY - eyeHeight / 2 - 8, eyeWidth + 10, 5);
        g2d.fillRect(centerX + eyeSpacing - eyeWidth / 2 - 5, centerY - eyeHeight / 2 - 8, eyeWidth + 10, 5);
    }

    private static void drawMouth(Graphics2D g2d, int centerX, int centerY, int mouthWidth, int mouthHeight, boolean yawning) {
        if (yawning) {
            g2d.setColor(MOUTH_RED);
            g2d.fillOval(centerX - mouthWidth / 2, centerY - mouthHeight / 2, mouthWidth, mouthHeight);

            g2d.setColor(new Color(120, 25, 20));
            g2d.fillOval(centerX - mouthWidth / 2 + 10, centerY - mouthHeight / 2 + 10, mouthWidth - 20, mouthHeight - 20);

            g2d.setColor(new Color(255, 200, 180));
            g2d.fillOval(centerX - mouthWidth / 3, centerY - mouthHeight / 3, mouthWidth / 2, mouthHeight / 4);

            Random random = new Random(789);
            for (int y = centerY - mouthHeight / 2; y < centerY + mouthHeight / 2; y++) {
                for (int x = centerX - mouthWidth / 2; x < centerX + mouthWidth / 2; x++) {
                    int dx = x - centerX;
                    int dy = y - centerY;
                    if ((dx * dx) * 4 + (dy * dy) * 2.5 < (mouthWidth * mouthWidth) / 4) {
                        int variation = random.nextInt(30) - 15;
                        int r = Math.max(0, Math.min(255, MOUTH_RED.getRed() + variation));
                        int g = Math.max(0, Math.min(100, MOUTH_RED.getGreen() + variation));
                        int b = Math.max(0, Math.min(100, MOUTH_RED.getBlue() + variation));
                        if (r > 100 && g < 80 && b < 80 && r > g + 20 && r > b + 20) {
                            g2d.setColor(new Color(r, g, b));
                            g2d.fillRect(x, y, 1, 1);
                        }
                    }
                }
            }
        } else {
            g2d.setColor(new Color(180, 80, 70));
            g2d.fillOval(centerX - mouthWidth / 2, centerY - mouthHeight / 2, mouthWidth, mouthHeight);

            g2d.setColor(new Color(150, 60, 50));
            g2d.fillRect(centerX - mouthWidth / 2, centerY - 2, mouthWidth, 4);
        }
    }

    private static void saveImage(BufferedImage image, String filename) throws IOException {
        File outputFile = new File(OUTPUT_DIR + filename);
        ImageIO.write(image, "png", outputFile);
    }
}
