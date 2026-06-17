package com.traffic.alert.websocket;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@ServerEndpoint("/ws/alert/{userId}")
public class AlertWebSocket {

    private static final ConcurrentHashMap<Long, Session> SESSION_POOL = new ConcurrentHashMap<>();
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
        int count = ONLINE_COUNT.decrementAndGet();
        log.info("用户[{}]关闭WebSocket连接，当前在线数: {}", userId, count);
    }

    @OnMessage
    public void onMessage(String message, Session session, @PathParam("userId") Long userId) {
        log.debug("收到用户[{}]消息: {}", userId, message);
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
        String message = JSON.toJSONString(java.util.Map.of(
                "type", "ALERT",
                "data", alertData,
                "timestamp", System.currentTimeMillis()
        ));
        broadcastMessage(message);
    }

    public static int getOnlineCount() {
        return ONLINE_COUNT.get();
    }
}
