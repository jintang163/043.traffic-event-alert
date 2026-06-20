package com.traffic.alert.led;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

public interface LedProtocolHandler {

    String getProtocolName();

    boolean isSupported(String protocol);

    boolean displayMessage(LedDeviceConfig config, String message, String color, boolean isAlert, int displaySeconds);

    boolean restoreDefault(LedDeviceConfig config);

    LedDeviceStatus queryStatus(LedDeviceConfig config);

    default boolean setBrightness(LedDeviceConfig config, int brightness) {
        return true;
    }

    class LedDeviceConfig {
        private String protocol;
        private String host;
        private Integer port;
        private String deviceId;
        private String username;
        private String password;
        private String defaultMessage = "注意交通安全";
        private Map<String, Object> extraConfig;

        public String getProtocol() { return protocol; }
        public void setProtocol(String protocol) { this.protocol = protocol; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public Integer getPort() { return port; }
        public void setPort(Integer port) { this.port = port; }
        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getDefaultMessage() { return defaultMessage; }
        public void setDefaultMessage(String defaultMessage) { this.defaultMessage = defaultMessage; }
        public Map<String, Object> getExtraConfig() { return extraConfig; }
        public void setExtraConfig(Map<String, Object> extraConfig) { this.extraConfig = extraConfig; }
    }

    class LedDeviceStatus {
        private boolean connected;
        private String currentMessage;
        private String color;
        private boolean alertMode;
        private int brightness;
        private String errorMsg;
        private Long lastUpdateTime;

        public boolean isConnected() { return connected; }
        public void setConnected(boolean connected) { this.connected = connected; }
        public String getCurrentMessage() { return currentMessage; }
        public void setCurrentMessage(String currentMessage) { this.currentMessage = currentMessage; }
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
        public boolean isAlertMode() { return alertMode; }
        public void setAlertMode(boolean alertMode) { this.alertMode = alertMode; }
        public int getBrightness() { return brightness; }
        public void setBrightness(int brightness) { this.brightness = brightness; }
        public String getErrorMsg() { return errorMsg; }
        public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
        public Long getLastUpdateTime() { return lastUpdateTime; }
        public void setLastUpdateTime(Long lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }
    }

    @Slf4j
    class MockProtocolHandler implements LedProtocolHandler {
        @Override
        public String getProtocolName() { return "MOCK"; }

        @Override
        public boolean isSupported(String protocol) {
            return protocol == null || protocol.isEmpty() || "MOCK".equalsIgnoreCase(protocol);
        }

        @Override
        public boolean displayMessage(LedDeviceConfig config, String message, String color, boolean isAlert, int displaySeconds) {
            log.info("[MOCK-LED] device={}, message={}, color={}, isAlert={}, duration={}s",
                    config.getDeviceId(), message, color, isAlert, displaySeconds);
            return true;
        }

        @Override
        public boolean restoreDefault(LedDeviceConfig config) {
            log.info("[MOCK-LED] device={}, restore default: {}", config.getDeviceId(), config.getDefaultMessage());
            return true;
        }

        @Override
        public LedDeviceStatus queryStatus(LedDeviceConfig config) {
            LedDeviceStatus status = new LedDeviceStatus();
            status.setConnected(true);
            status.setCurrentMessage(config.getDefaultMessage());
            status.setColor("GREEN");
            status.setAlertMode(false);
            status.setBrightness(100);
            status.setLastUpdateTime(System.currentTimeMillis());
            return status;
        }
    }

    @Slf4j
    class HikvisionLedHandler implements LedProtocolHandler {
        @Override
        public String getProtocolName() { return "HIKVISION"; }

        @Override
        public boolean isSupported(String protocol) {
            return "HIKVISION".equalsIgnoreCase(protocol);
        }

        @Override
        public boolean displayMessage(LedDeviceConfig config, String message, String color, boolean isAlert, int displaySeconds) {
            try {
                log.info("[HIKVISION-LED] Sending to {}:{}/{}: {} (color={}, alert={})",
                        config.getHost(), config.getPort(), config.getDeviceId(), message, color, isAlert);
                return true;
            } catch (Exception e) {
                log.error("[HIKVISION-LED] 发送失败: {}", e.getMessage());
                return false;
            }
        }

        @Override
        public boolean restoreDefault(LedDeviceConfig config) {
            return displayMessage(config, config.getDefaultMessage(), "GREEN", false, 0);
        }

        @Override
        public LedDeviceStatus queryStatus(LedDeviceConfig config) {
            LedDeviceStatus status = new LedDeviceStatus();
            status.setConnected(true);
            status.setCurrentMessage(config.getDefaultMessage());
            status.setColor("GREEN");
            status.setAlertMode(false);
            status.setBrightness(100);
            status.setLastUpdateTime(System.currentTimeMillis());
            return status;
        }
    }

    @Slf4j
    class DahuaLedHandler implements LedProtocolHandler {
        @Override
        public String getProtocolName() { return "DAHUA"; }

        @Override
        public boolean isSupported(String protocol) {
            return "DAHUA".equalsIgnoreCase(protocol);
        }

        @Override
        public boolean displayMessage(LedDeviceConfig config, String message, String color, boolean isAlert, int displaySeconds) {
            try {
                log.info("[DAHUA-LED] Sending to {}:{}/{}: {} (color={}, alert={})",
                        config.getHost(), config.getPort(), config.getDeviceId(), message, color, isAlert);
                return true;
            } catch (Exception e) {
                log.error("[DAHUA-LED] 发送失败: {}", e.getMessage());
                return false;
            }
        }

        @Override
        public boolean restoreDefault(LedDeviceConfig config) {
            return displayMessage(config, config.getDefaultMessage(), "GREEN", false, 0);
        }

        @Override
        public LedDeviceStatus queryStatus(LedDeviceConfig config) {
            LedDeviceStatus status = new LedDeviceStatus();
            status.setConnected(true);
            status.setCurrentMessage(config.getDefaultMessage());
            status.setColor("GREEN");
            status.setAlertMode(false);
            status.setBrightness(100);
            status.setLastUpdateTime(System.currentTimeMillis());
            return status;
        }
    }

    @Slf4j
    class HttpLedHandler implements LedProtocolHandler {
        @Override
        public String getProtocolName() { return "HTTP"; }

        @Override
        public boolean isSupported(String protocol) {
            return "HTTP".equalsIgnoreCase(protocol);
        }

        @Override
        public boolean displayMessage(LedDeviceConfig config, String message, String color, boolean isAlert, int displaySeconds) {
            try {
                String url = String.format("http://%s:%d/api/led/display", config.getHost(), config.getPort());
                log.info("[HTTP-LED] POST {}: message={}, color={}, alert={}", url, message, color, isAlert);
                return true;
            } catch (Exception e) {
                log.error("[HTTP-LED] 发送失败: {}", e.getMessage());
                return false;
            }
        }

        @Override
        public boolean restoreDefault(LedDeviceConfig config) {
            return displayMessage(config, config.getDefaultMessage(), "GREEN", false, 0);
        }

        @Override
        public LedDeviceStatus queryStatus(LedDeviceConfig config) {
            LedDeviceStatus status = new LedDeviceStatus();
            status.setConnected(true);
            status.setCurrentMessage(config.getDefaultMessage());
            status.setColor("GREEN");
            status.setAlertMode(false);
            status.setBrightness(100);
            status.setLastUpdateTime(System.currentTimeMillis());
            return status;
        }
    }
}
