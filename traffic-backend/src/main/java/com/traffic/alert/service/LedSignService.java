package com.traffic.alert.service;

import com.alibaba.fastjson2.JSON;
import com.traffic.alert.entity.Camera;
import com.traffic.alert.led.LedProtocolHandler;
import com.traffic.alert.websocket.AlertWebSocket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class LedSignService {

    private final Map<Long, LedSignStatus> signStatusMap = new ConcurrentHashMap<>();
    private final List<LedProtocolHandler> protocolHandlers;
    private final CameraService cameraService;

    public LedSignService(CameraService cameraService) {
        this.cameraService = cameraService;
        this.protocolHandlers = new ArrayList<>();
        this.protocolHandlers.add(new LedProtocolHandler.MockProtocolHandler());
        this.protocolHandlers.add(new LedProtocolHandler.HikvisionLedHandler());
        this.protocolHandlers.add(new LedProtocolHandler.DahuaLedHandler());
        this.protocolHandlers.add(new LedProtocolHandler.HttpLedHandler());
    }

    public static class LedSignStatus {
        private Long cameraId;
        private String currentMessage;
        private String defaultMessage = "注意交通安全";
        private LocalDateTime lastUpdateTime;
        private boolean isAlertMode = false;
        private int brightness = 100;
        private String color = "RED";
        private boolean connected = true;
        private String protocol = "MOCK";
        private String errorMsg;

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
        public boolean isConnected() { return connected; }
        public void setConnected(boolean connected) { this.connected = connected; }
        public String getProtocol() { return protocol; }
        public void setProtocol(String protocol) { this.protocol = protocol; }
        public String getErrorMsg() { return errorMsg; }
        public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    }

    public LedSignStatus getStatus(Long cameraId) {
        return signStatusMap.computeIfAbsent(cameraId, id -> initStatus(id));
    }

    private LedSignStatus initStatus(Long cameraId) {
        LedSignStatus status = new LedSignStatus();
        status.setCameraId(cameraId);

        try {
            Map<String, Object> ledConfig = cameraService.getLedConfig(cameraId);
            if (ledConfig != null && !ledConfig.isEmpty()) {
                status.setProtocol(ledConfig.get("protocol") != null ? (String) ledConfig.get("protocol") : "MOCK");
                if (ledConfig.get("defaultMessage") != null) {
                    status.setDefaultMessage((String) ledConfig.get("defaultMessage"));
                }
            }
        } catch (Exception e) {
            log.warn("读取摄像头[{}]LED配置失败，使用默认配置: {}", cameraId, e.getMessage());
        }

        status.setCurrentMessage(status.getDefaultMessage());
        status.setColor("GREEN");
        status.setLastUpdateTime(LocalDateTime.now());

        try {
            LedProtocolHandler.LedDeviceConfig deviceConfig = buildDeviceConfig(cameraId, status);
            LedProtocolHandler handler = getHandler(deviceConfig);
            LedProtocolHandler.LedDeviceStatus deviceStatus = handler.queryStatus(deviceConfig);
            if (deviceStatus != null) {
                status.setConnected(deviceStatus.isConnected());
                if (deviceStatus.getCurrentMessage() != null) {
                    status.setCurrentMessage(deviceStatus.getCurrentMessage());
                }
            }
        } catch (Exception e) {
            log.warn("查询LED设备[{}]实际状态失败: {}", cameraId, e.getMessage());
            status.setConnected(false);
            status.setErrorMsg(e.getMessage());
        }

        return status;
    }

    public boolean displayPedestrianWarning(Long cameraId) {
        return displayMessage(cameraId, "行人请离开", "RED", true, 30);
    }

    public boolean displayMessage(Long cameraId, String message, String color, boolean isAlert, int displaySeconds) {
        LedSignStatus status = getStatus(cameraId);
        LedProtocolHandler.LedDeviceConfig deviceConfig = buildDeviceConfig(cameraId, status);
        LedProtocolHandler handler = getHandler(deviceConfig);

        boolean hardwareSuccess = false;
        try {
            hardwareSuccess = handler.displayMessage(deviceConfig, message, color, isAlert, displaySeconds);
        } catch (Exception e) {
            log.error("LED情报板硬件发送失败: cameraId={}, message={}, error={}", cameraId, message, e.getMessage());
            status.setErrorMsg(e.getMessage());
        }

        status.setCurrentMessage(message);
        status.setAlertMode(isAlert);
        status.setColor(color);
        status.setLastUpdateTime(LocalDateTime.now());
        status.setConnected(hardwareSuccess);

        broadcastStatus(cameraId, status);

        log.info("LED情报板[摄像头{}]显示: {}, 颜色: {}, 告警模式: {}, 硬件状态: {}",
                cameraId, message, color, isAlert, hardwareSuccess ? "成功" : "失败");

        if (isAlert) {
            scheduleRestore(cameraId, displaySeconds);
        }

        return true;
    }

    public boolean restoreDefault(Long cameraId) {
        LedSignStatus status = getStatus(cameraId);
        LedProtocolHandler.LedDeviceConfig deviceConfig = buildDeviceConfig(cameraId, status);
        LedProtocolHandler handler = getHandler(deviceConfig);

        boolean hardwareSuccess = false;
        try {
            hardwareSuccess = handler.restoreDefault(deviceConfig);
        } catch (Exception e) {
            log.error("LED情报板硬件恢复默认失败: cameraId={}, error={}", cameraId, e.getMessage());
        }

        status.setCurrentMessage(status.getDefaultMessage());
        status.setAlertMode(false);
        status.setColor("GREEN");
        status.setLastUpdateTime(LocalDateTime.now());
        status.setConnected(hardwareSuccess);
        status.setErrorMsg(null);

        broadcastStatus(cameraId, status);

        log.info("LED情报板[摄像头{}]恢复默认显示, 硬件状态: {}", cameraId, hardwareSuccess ? "成功" : "失败");
        return true;
    }

    private void scheduleRestore(Long cameraId, int delaySeconds) {
        Thread restoreThread = new Thread(() -> {
            try {
                Thread.sleep(delaySeconds * 1000L);
                LedSignStatus status = signStatusMap.get(cameraId);
                if (status != null && status.isAlertMode()) {
                    restoreDefault(cameraId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        restoreThread.setDaemon(true);
        restoreThread.setName("led-restore-" + cameraId);
        restoreThread.start();
    }

    public boolean setDefaultMessage(Long cameraId, String message) {
        LedSignStatus status = getStatus(cameraId);
        status.setDefaultMessage(message);
        if (!status.isAlertMode()) {
            status.setCurrentMessage(message);
            broadcastStatus(cameraId, status);
        }
        return true;
    }

    public boolean setBrightness(Long cameraId, int brightness) {
        LedSignStatus status = getStatus(cameraId);
        int finalBrightness = Math.max(0, Math.min(100, brightness));

        LedProtocolHandler.LedDeviceConfig deviceConfig = buildDeviceConfig(cameraId, status);
        LedProtocolHandler handler = getHandler(deviceConfig);
        try {
            handler.setBrightness(deviceConfig, finalBrightness);
        } catch (Exception e) {
            log.warn("设置LED亮度失败: cameraId={}, brightness={}, error={}", cameraId, finalBrightness, e.getMessage());
        }

        status.setBrightness(finalBrightness);
        status.setLastUpdateTime(LocalDateTime.now());
        broadcastStatus(cameraId, status);
        return true;
    }

    private LedProtocolHandler getHandler(LedProtocolHandler.LedDeviceConfig config) {
        for (LedProtocolHandler handler : protocolHandlers) {
            if (handler.isSupported(config.getProtocol())) {
                return handler;
            }
        }
        return protocolHandlers.get(0);
    }

    private LedProtocolHandler.LedDeviceConfig buildDeviceConfig(Long cameraId, LedSignStatus status) {
        LedProtocolHandler.LedDeviceConfig config = new LedProtocolHandler.LedDeviceConfig();
        config.setProtocol(status.getProtocol());
        config.setDefaultMessage(status.getDefaultMessage());

        try {
            Map<String, Object> ledConfig = cameraService.getLedConfig(cameraId);
            if (ledConfig != null && !ledConfig.isEmpty()) {
                if (ledConfig.get("host") != null) config.setHost((String) ledConfig.get("host"));
                if (ledConfig.get("port") != null) config.setPort(((Number) ledConfig.get("port")).intValue());
                if (ledConfig.get("deviceId") != null) config.setDeviceId((String) ledConfig.get("deviceId"));
                if (ledConfig.get("username") != null) config.setUsername((String) ledConfig.get("username"));
                if (ledConfig.get("password") != null) config.setPassword((String) ledConfig.get("password"));
                if (ledConfig.get("defaultMessage") != null) config.setDefaultMessage((String) ledConfig.get("defaultMessage"));
                if (ledConfig.get("protocol") != null) config.setProtocol((String) ledConfig.get("protocol"));
                config.setExtraConfig(ledConfig);
            }
        } catch (Exception e) {
            log.debug("读取LED设备配置失败: cameraId={}, error={}", cameraId, e.getMessage());
        }

        return config;
    }

    private void broadcastStatus(Long cameraId, LedSignStatus status) {
        try {
            Map<String, Object> payload = JSON.parseObject(JSON.toJSONString(status), Map.class);
            AlertWebSocket.sendLedStatusUpdate(cameraId, payload);
        } catch (Exception e) {
            log.warn("广播LED状态更新失败: cameraId={}, error={}", cameraId, e.getMessage());
        }
    }

    public List<String> getSupportedProtocols() {
        return protocolHandlers.stream().map(LedProtocolHandler::getProtocolName).toList();
    }

    public boolean refreshDeviceStatus(Long cameraId) {
        LedSignStatus status = signStatusMap.get(cameraId);
        if (status == null) {
            status = initStatus(cameraId);
        } else {
            LedProtocolHandler.LedDeviceConfig deviceConfig = buildDeviceConfig(cameraId, status);
            LedProtocolHandler handler = getHandler(deviceConfig);
            try {
                LedProtocolHandler.LedDeviceStatus deviceStatus = handler.queryStatus(deviceConfig);
                if (deviceStatus != null) {
                    status.setConnected(deviceStatus.isConnected());
                    status.setErrorMsg(deviceStatus.getErrorMsg());
                    if (deviceStatus.getCurrentMessage() != null) {
                        status.setCurrentMessage(deviceStatus.getCurrentMessage());
                    }
                    status.setColor(deviceStatus.getColor() != null ? deviceStatus.getColor() : status.getColor());
                    status.setAlertMode(deviceStatus.isAlertMode());
                    status.setBrightness(deviceStatus.getBrightness());
                    status.setLastUpdateTime(LocalDateTime.now());
                    broadcastStatus(cameraId, status);
                }
            } catch (Exception e) {
                status.setConnected(false);
                status.setErrorMsg(e.getMessage());
                log.warn("刷新LED设备状态失败: cameraId={}, error={}", cameraId, e.getMessage());
                return false;
            }
        }
        return status.isConnected();
    }
}
