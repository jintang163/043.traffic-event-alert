package com.traffic.alert.service;

import com.alibaba.fastjson2.JSON;
import com.traffic.alert.config.NotificationConfig;
import com.traffic.alert.entity.AlertEvent;
import com.traffic.alert.enums.DebrisCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationConfig notificationConfig;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public void sendAlertNotification(AlertEvent alert) {
        String content = buildAlertContent(alert);
        if (notificationConfig.getDingTalk().isEnabled()) {
            sendDingTalkNotification(content);
        }
        if (notificationConfig.getWeChat().isEnabled()) {
            sendWeChatNotification(content);
        }
        if (notificationConfig.getSms().isEnabled()) {
            sendSmsNotification(alert, content);
        }
    }

    private String buildAlertContent(AlertEvent alert) {
        String levelText = switch (alert.getEventLevel()) {
            case 3 -> "【紧急】";
            case 2 -> "【严重】";
            default -> "【一般】";
        };
        String typeText = switch (alert.getEventType()) {
            case "ACCIDENT" -> "交通事故";
            case "REVERSE" -> "车辆逆行";
            case "DEBRIS" -> {
                if (alert.getDebrisCategory() != null && !alert.getDebrisCategory().isEmpty()) {
                    yield "抛洒物-" + DebrisCategory.of(alert.getDebrisCategory()).getLabel();
                }
                yield "路面抛洒物";
            }
            default -> alert.getEventType();
        };

        String categoryLine = "";
        if ("DEBRIS".equals(alert.getEventType()) && alert.getDebrisCategory() != null) {
            DebrisCategory dc = DebrisCategory.of(alert.getDebrisCategory());
            categoryLine = String.format(
                    "抛洒物子类: %s (编码: %s, 预设等级: L%d)%n",
                    dc.getLabel(), dc.getCode(), dc.getDefaultLevel());
        }

        return String.format("%s 交通事件告警\n" +
                        "事件类型: %s\n" +
                        "%s" +
                        "摄像头: %s\n" +
                        "位置: %s\n" +
                        "时间: %s\n" +
                        "置信度: %.2f%%\n" +
                        "描述: %s",
                levelText, typeText,
                categoryLine,
                alert.getCameraName(),
                alert.getLocation(),
                alert.getEventTime(),
                alert.getConfidence() != null ? alert.getConfidence().multiply(java.math.BigDecimal.valueOf(100)) : 0,
                alert.getDescription()
        );
    }

    private void sendDingTalkNotification(String content) {
        try {
            NotificationConfig.DingTalk config = notificationConfig.getDingTalk();
            long timestamp = Instant.now().toEpochMilli();
            String sign = generateDingTalkSign(timestamp, config.getSecret());

            String url = config.getWebhook() + "&timestamp=" + timestamp + "&sign=" + sign;

            Map<String, Object> body = Map.of(
                    "msgtype", "text",
                    "text", Map.of("content", content)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("钉钉通知发送结果: {}", response.body());
        } catch (Exception e) {
            log.error("钉钉通知发送失败: {}", e.getMessage());
        }
    }

    private String generateDingTalkSign(long timestamp, String secret) throws Exception {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().encodeToString(signData);
    }

    private void sendWeChatNotification(String content) {
        try {
            NotificationConfig.WeChat config = notificationConfig.getWeChat();

            Map<String, Object> body = Map.of(
                    "msgtype", "markdown",
                    "markdown", Map.of(
                            "content", content
                    )
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getWebhook()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("企业微信通知发送结果: {}", response.body());
        } catch (Exception e) {
            log.error("企业微信通知发送失败: {}", e.getMessage());
        }
    }

    private void sendSmsNotification(AlertEvent alert, String content) {
        try {
            NotificationConfig.Sms config = notificationConfig.getSms();

            Map<String, Object> body = Map.of(
                    "apiKey", config.getApiKey(),
                    "templateId", config.getTemplateId(),
                    "content", content
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getApiUrl()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("短信通知发送结果: {}", response.body());
        } catch (Exception e) {
            log.error("短信通知发送失败: {}", e.getMessage());
        }
    }
}
