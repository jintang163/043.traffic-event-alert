package com.traffic.alert.service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

public class TestImageGenerator {

    private static final int WIDTH = 640;
    private static final int HEIGHT = 480;
    private static final String OUTPUT_DIR = "src/test/resources/video-quality-samples/";

    public static void main(String[] args) throws IOException {
        generateNormalScene();
        generateBlackScreen();
        generateLowQualityScene();
        generateOccludedScene();
        generateFreezeScenes();
        System.out.println("所有测试图像生成完成！");
    }

    private static void generateNormalScene() throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(123);

        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int baseR, baseG, baseB;
                if (y < HEIGHT / 2) {
                    baseR = 200 + random.nextInt(30);
                    baseG = 200 + random.nextInt(30);
                    baseB = 210 + random.nextInt(30);
                } else {
                    baseR = 50 + random.nextInt(20);
                    baseG = 50 + random.nextInt(20);
                    baseB = 55 + random.nextInt(20);
                }

                if ((x / 4 + y / 4) % 2 == 0) {
                    baseR = Math.min(255, baseR + 25);
                    baseG = Math.min(255, baseG + 25);
                    baseB = Math.min(255, baseB + 25);
                } else {
                    baseR = Math.max(0, baseR - 25);
                    baseG = Math.max(0, baseG - 25);
                    baseB = Math.max(0, baseB - 25);
                }

                int color = (baseR << 16) | (baseG << 8) | baseB;
                image.setRGB(x, y, color);
            }
        }

        Graphics2D g2d = image.createGraphics();

        g2d.setColor(new Color(255, 255, 255));
        for (int y = 10; y < HEIGHT / 2 - 10; y += 8) {
            g2d.fillRect(0, y, WIDTH, 2);
        }

        for (int x = 10; x < WIDTH - 10; x += 8) {
            g2d.fillRect(x, HEIGHT / 2 + 10, 2, HEIGHT / 2 - 20);
        }

        g2d.setColor(new Color(255, 255, 0));
        g2d.fillRect(0, HEIGHT / 2, 10, HEIGHT / 2);
        g2d.fillRect(WIDTH - 10, HEIGHT / 2, 10, HEIGHT / 2);

        g2d.setColor(new Color(0, 0, 0));
        g2d.fillRect(100, HEIGHT / 2 - 60, 120, 60);
        g2d.fillRect(300, HEIGHT / 2 - 70, 140, 70);

        g2d.setColor(new Color(255, 0, 0));
        g2d.fillRect(120, HEIGHT / 2 - 55, 20, 18);
        g2d.fillRect(180, HEIGHT / 2 - 55, 20, 18);

        g2d.dispose();

        saveImage(image, "01-normal-scene.png");
        System.out.println("生成: 01-normal-scene.png (正常路况)");
    }

    private static void generateBlackScreen() throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, WIDTH, HEIGHT);

        g2d.dispose();

        saveImage(image, "02-black-screen.png");
        System.out.println("生成: 02-black-screen.png (纯黑画面)");
    }

    private static void generateLowQualityScene() throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);

        double targetMean = 30.0;
        double targetStd = 12.0;

        double[] values = new double[WIDTH * HEIGHT];
        for (int i = 0; i < values.length; i++) {
            values[i] = random.nextGaussian() * targetStd + targetMean;
        }

        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int idx = y * WIDTH + x;
                int brightness = (int) Math.max(0, Math.min(255, values[idx]));
                int color = (brightness << 16) | (brightness << 8) | brightness;
                image.setRGB(x, y, color);
            }
        }

        BufferedImage blurred = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        for (int y = 1; y < HEIGHT - 1; y++) {
            for (int x = 1; x < WIDTH - 1; x++) {
                long sumR = 0, sumG = 0, sumB = 0;
                int count = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int rgb = image.getRGB(x + dx, y + dy);
                        sumR += (rgb >> 16) & 0xFF;
                        sumG += (rgb >> 8) & 0xFF;
                        sumB += rgb & 0xFF;
                        count++;
                    }
                }
                int r = (int) (sumR / count);
                int g = (int) (sumG / count);
                int b = (int) (sumB / count);
                blurred.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }

        double sum = 0, sumSq = 0;
        int n = 0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int lum = (blurred.getRGB(x, y) >> 16) & 0xFF;
                sum += lum;
                sumSq += lum * lum;
                n++;
            }
        }
        double mean = sum / n;
        double std = Math.sqrt(sumSq / n - mean * mean);

        if (std > 0) {
            double scale = targetStd / std;
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    int rgb = blurred.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    
                    r = (int) Math.max(0, Math.min(255, targetMean + (r - mean) * scale));
                    g = (int) Math.max(0, Math.min(255, targetMean + (g - mean) * scale));
                    b = (int) Math.max(0, Math.min(255, targetMean + (b - mean) * scale));
                    
                    blurred.setRGB(x, y, (r << 16) | (g << 8) | b);
                }
            }
        }

        saveImage(blurred, "03-low-quality.png");
        System.out.println("生成: 03-low-quality.png (低亮+低对比+轻微模糊)");
    }

    private static void generateOccludedScene() throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setColor(new Color(40, 40, 40));
        g2d.fillRect(0, 0, WIDTH, HEIGHT);

        g2d.setColor(new Color(35, 35, 35));
        g2d.fillRect(0, HEIGHT / 2, WIDTH, HEIGHT / 2);

        g2d.setColor(new Color(55, 55, 55));
        for (int i = 0; i < WIDTH; i += 60) {
            g2d.fillRect(i, HEIGHT / 2 + 50, 30, 8);
        }

        g2d.setColor(new Color(25, 25, 25));
        g2d.fillRect(0, 0, WIDTH / 2, HEIGHT / 2);

        g2d.dispose();

        saveImage(image, "04-occluded.png");
        System.out.println("生成: 04-occluded.png (严重遮挡-左上角50%区域)");
    }

    private static void generateFreezeScenes() throws IOException {
        BufferedImage frame1 = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d1 = frame1.createGraphics();

        g2d1.setColor(new Color(125, 125, 125));
        g2d1.fillRect(0, 0, WIDTH, HEIGHT);

        g2d1.setColor(new Color(75, 75, 75));
        g2d1.fillRect(0, HEIGHT / 2, WIDTH, HEIGHT / 2);

        g2d1.setColor(new Color(195, 195, 195));
        for (int i = 0; i < WIDTH; i += 60) {
            g2d1.fillRect(i, HEIGHT / 2 + 50, 30, 8);
        }

        g2d1.setColor(new Color(55, 55, 55));
        g2d1.fillRect(150, HEIGHT / 2 - 45, 90, 45);
        g2d1.fillRect(300, HEIGHT / 2 - 55, 110, 55);

        g2d1.dispose();

        saveImage(frame1, "05-freeze-frame1.png");
        System.out.println("生成: 05-freeze-frame1.png (冻结场景-帧1)");

        BufferedImage frame2 = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int rgb = frame1.getRGB(x, y);
                int r = Math.min(255, Math.max(0, ((rgb >> 16) & 0xFF)));
                int g = Math.min(255, Math.max(0, ((rgb >> 8) & 0xFF)));
                int b = Math.min(255, Math.max(0, (rgb & 0xFF)));
                frame2.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }

        saveImage(frame2, "05-freeze-frame2.png");
        System.out.println("生成: 05-freeze-frame2.png (冻结场景-帧2,几乎与帧1相同)");
    }

    private static void saveImage(BufferedImage image, String filename) throws IOException {
        File outputFile = new File(OUTPUT_DIR + filename);
        ImageIO.write(image, "png", outputFile);
    }
}
