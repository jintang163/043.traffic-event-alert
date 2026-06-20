package com.traffic.alert.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class LedSignService {

    private final Map<Long, LedSignStatus> signStatusMap = new ConcurrentHashMap<>();

    public static class LedSignStatus {
        private Long cameraId;
        private String currentMessage;
        private String defaultMessage = "注意交通安全";
        private LocalDateTime lastUpdateTime;
        private boolean isAlertMode = false;
        private int brightness = 100;
        private String color = "RED";

        public Long getCameraId() { return cameraId; }
        public void setCameraId(Long cameraId) { this.cameraId = cameraId; }
        public String getCurrentMessage() { return currentMessage; }
        public void setCurrentMessage(String currentMessage) { this.currentMessage = currentMessage; }
        public String getDefaultMessage() { return defaultMessage; }
        public void setDefaultMessage(String defaultMessage) { this.defaultMessage = defaultMessage; }
        public LocalDateTime getLastUpdateTime() { return lastUpdateTime; }
        public void setLastUpdateTime(LocalDateTime lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }
        public boolean isAlertMode() { return isAlertMode; }
        public void setAlertMode(boolean alertMode) { isAlertMode = alertMode; }
        public int getBrightness() { return brightness; }
        public void setBrightness(int brightness) { this.brightness = brightness; }
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
    }

    public LedSignStatus getStatus(Long cameraId) {
        return signStatusMap.computeIfAbsent(cameraId, id -> {
            LedSignStatus status = new LedSignStatus();
            status.setCameraId(id);
            status.setCurrentMessage(status.getDefaultMessage());
            status.setLastUpdateTime(LocalDateTime.now());
            return status;
        });
    }

    public boolean displayPedestrianWarning(Long cameraId) {
        return displayMessage(cameraId, "行人请离开", "RED", true, 30);
    }

    public boolean displayMessage(Long cameraId, String message, String color, boolean isAlert, int displaySeconds) {
        try {
            LedSignStatus status = getStatus(cameraId);
            status.setCurrentMessage(message);
            status.setAlertMode(isAlert);
            status.setColor(color);
            status.setLastUpdateTime(LocalDateTime.now());

            sendToHardware(cameraId, message, color, isAlert, displaySeconds);

            log.info("LED情报板[摄像头{}]显示: {}, 颜色: {}, 告警模式: {}", cameraId, message, color, isAlert);

            if (isAlert) {
                scheduleRestore(cameraId, displaySeconds);
            }

            return true;
        } catch (Exception e) {
            log.error("LED情报板显示失败: cameraId={}, message={}, error={}", cameraId, message, e.getMessage());
            return false;
        }
    }

    public boolean restoreDefault(Long cameraId) {
        try {
            LedSignStatus status = getStatus(cameraId);
            status.setCurrentMessage(status.getDefaultMessage());
            status.setAlertMode(false);
            status.setColor("GREEN");
            status.setLastUpdateTime(LocalDateTime.now());

            sendToHardware(cameraId, status.getDefaultMessage(), "GREEN", false, 0);

            log.info("LED情报板[摄像头{}]恢复默认显示", cameraId);
            return true;
        } catch (Exception e) {
            log.error("LED情报板恢复默认失败: cameraId={}, error={}", cameraId, e.getMessage());
            return false;
        }
    }

    private void sendToHardware(Long cameraId, String message, String color, boolean isAlert, int displaySeconds) {
        log.debug("[模拟硬件调用] LED情报板 cameraId={}, message={}, color={}, isAlert={}",
                cameraId, message, color, isAlert);
    }

    private void scheduleRestore(Long cameraId, int delaySeconds) {
        new Thread(() -> {
            try {
                Thread.sleep(delaySeconds * 1000L);
                LedSignStatus status = signStatusMap.get(cameraId);
                if (status != null && status.isAlertMode()) {
                    restoreDefault(cameraId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    public boolean setDefaultMessage(Long cameraId, String message) {
        LedSignStatus status = getStatus(cameraId);
        status.setDefaultMessage(message);
        if (!status.isAlertMode()) {
            status.setCurrentMessage(message);
        }
        return true;
    }

    public boolean setBrightness(Long cameraId, int brightness) {
        LedSignStatus status = getStatus(cameraId);
        status.setBrightness(Math.max(0, Math.min(100, brightness)));
        return true;
    }
}
