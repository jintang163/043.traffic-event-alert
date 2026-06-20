package com.traffic.alert.websocket;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@ServerEndpoint("/ws/alert/{userId}")
public class AlertWebSocket {

    private static final ConcurrentHashMap<Long, Session> SESSION_POOL = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, List<Long>> USER_CAMERA_SUBSCRIPTIONS = new ConcurrentHashMap<>();
    private static final AtomicInteger ONLINE_COUNT = new AtomicInteger(0);

    @OnOpen
    public void onOpen(Session session, @PathParam("userId") Long userId) {
        SESSION_POOL.put(userId, session);
        int count = ONLINE_COUNT.incrementAndGet();
        log.info("用户[{}]建立WebSocket连接，当前在线数: {}", userId, count);
        sendMessage(userId, JSON.toJSONString(java.util.Map.of(
                "type", "CONNECTED",
                "message", "连接成功",
                "onlineCount", count
        )));
    }

    @OnClose
    public void onClose(Session session, @PathParam("userId") Long userId) {
        SESSION_POOL.remove(userId);
        USER_CAMERA_SUBSCRIPTIONS.remove(userId);
        int count = ONLINE_COUNT.decrementAndGet();
        log.info("用户[{}]关闭WebSocket连接，当前在线数: {}", userId, count);
    }

    @OnMessage
    public void onMessage(String message, Session session, @PathParam("userId") Long userId) {
        log.debug("收到用户[{}]消息: {}", userId, message);
        try {
            Map<String, Object> msg = JSON.parseObject(message, Map.class);
            String type = (String) msg.get("type");
            if ("SUBSCRIBE_CAMERAS".equals(type) && msg.get("cameraIds") != null) {
                @SuppressWarnings("unchecked")
                List<Long> cameraIds = (List<Long>) msg.get("cameraIds");
                USER_CAMERA_SUBSCRIPTIONS.put(userId, cameraIds);
                log.info("用户[{}]订阅摄像头: {}", userId, cameraIds);
            } else if ("UNSUBSCRIBE_CAMERAS".equals(type)) {
                USER_CAMERA_SUBSCRIPTIONS.remove(userId);
                log.info("用户[{}]取消摄像头订阅", userId);
            }
        } catch (Exception e) {
            log.warn("解析WebSocket消息失败: {}", message);
        }
    }

    @OnError
    public void onError(Session session, @PathParam("userId") Long userId, Throwable error) {
        log.error("用户[{}]WebSocket连接错误: {}", userId, error.getMessage());
    }

    public static void sendMessage(Long userId, String message) {
        Session session = SESSION_POOL.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
                log.debug("向用户[{}]推送消息: {}", userId, message);
            } catch (IOException e) {
                log.error("向用户[{}]推送消息失败: {}", userId, e.getMessage());
            }
        }
    }

    public static void broadcastMessage(String message) {
        log.info("广播消息，在线用户数: {}", SESSION_POOL.size());
        SESSION_POOL.forEach((userId, session) -> {
            if (session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(message);
                } catch (IOException e) {
                    log.error("向用户[{}]广播消息失败: {}", userId, e.getMessage());
                }
            }
        });
    }

    public static void sendAlertMessage(Object alertData) {
        sendAlertMessage(alertData, false);
    }

    public static void sendAlertMessage(Object alertData, boolean isMajor) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("type", isMajor ? "MAJOR_ALERT" : "ALERT");
        payload.put("data", alertData);
        payload.put("timestamp", System.currentTimeMillis());
        payload.put("major", isMajor);
        String message = JSON.toJSONString(payload);
        broadcastMessage(message);
    }

    public static void sendDetectionMessage(Long cameraId, Map<String, Object> detectionData) {
        String message = JSON.toJSONString(java.util.Map.of(
                "type", "DETECTION",
                "cameraId", cameraId,
                "data", detectionData,
                "timestamp", System.currentTimeMillis()
        ));

        SESSION_POOL.forEach((userId, session) -> {
            if (!session.isOpen()) {
                return;
            }
            List<Long> subscribed = USER_CAMERA_SUBSCRIPTIONS.get(userId);
            if (subscribed == null || subscribed.contains(cameraId)) {
                try {
                    session.getBasicRemote().sendText(message);
                } catch (IOException e) {
                    log.error("向用户[{}]推送检测框消息失败: {}", userId, e.getMessage());
                }
            }
        });
    }

    public static int getOnlineCount() {
        return ONLINE_COUNT.get();
    }

    public static void sendTrackUpdateEvent(Map<String, Object> trackEvent) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("type", "TRACK_UPDATE");
        payload.put("data", trackEvent);
        payload.put("timestamp", System.currentTimeMillis());
        broadcastMessage(JSON.toJSONString(payload));
    }

    public static void sendLedStatusUpdate(Long cameraId, Map<String, Object> ledStatus) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("type", "LED_STATUS_UPDATE");
        payload.put("cameraId", cameraId);
        payload.put("data", ledStatus);
        payload.put("timestamp", System.currentTimeMillis());
        broadcastMessage(JSON.toJSONString(payload));
    }

    public static void sendStormAlert(Map<String, Object> stormData) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("type", "STORM_ALERT");
        payload.put("data", stormData);
        payload.put("timestamp", System.currentTimeMillis());
        broadcastMessage(JSON.toJSONString(payload));
    }

    public static void sendStormRecovery(Map<String, Object> recoveryData) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("type", "STORM_RECOVERY");
        payload.put("data", recoveryData);
        payload.put("timestamp", System.currentTimeMillis());
        broadcastMessage(JSON.toJSONString(payload));
    }
}
