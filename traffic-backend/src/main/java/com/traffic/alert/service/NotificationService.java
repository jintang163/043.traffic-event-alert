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
        sendAlertNotification(alert, false);
    }

    public void sendAlertNotification(AlertEvent alert, boolean isMajor) {
        String content = buildAlertContent(alert, isMajor);
        if (isMajor) {
            if (notificationConfig.getDingTalk().isEnabled()) {
                sendDingTalkNotification(content, true);
            }
            if (notificationConfig.getWeChat().isEnabled()) {
                sendWeChatNotification(content, true);
            }
            if (notificationConfig.getSms().isEnabled()) {
                sendSmsNotification(alert, content, true);
            }
        } else {
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
    }

    private String buildAlertContent(AlertEvent alert) {
        return buildAlertContent(alert, false);
    }

    private String buildAlertContent(AlertEvent alert, boolean isMajor) {
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

        String severityLine = "";
        if ("ACCIDENT".equals(alert.getEventType()) && alert.getAccidentSeverity() != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("事故等级: %s%n", alert.getAccidentSeverityLabel()));
            if (alert.getAccidentVehicles() != null) {
                sb.append(String.format("涉事车辆: %d辆%n", alert.getAccidentVehicles()));
            }
            if (alert.getAccidentRollover() != null && alert.getAccidentRollover() == 1) {
                sb.append("特征: 车辆翻滚\n");
            }
            if (alert.getAccidentFire() != null && alert.getAccidentFire() == 1) {
                sb.append("特征: 车辆起火\n");
            }
            if (alert.getAccidentCasualty() != null && alert.getAccidentCasualty() > 0) {
                sb.append(String.format("人员伤亡: %d人%n", alert.getAccidentCasualty()));
            }
            if (alert.getAccidentImpactSpeed() != null) {
                sb.append(String.format("碰撞车速: %.1f km/h%n", alert.getAccidentImpactSpeed()));
            }
            severityLine = sb.toString();
        }

        String majorPrefix = isMajor ? "🚨 重大事故紧急告警\n" : "";

        return String.format("%s%s 交通事件告警\n" +
                        "事件类型: %s\n" +
                        "%s" +
                        "%s" +
                        "摄像头: %s\n" +
                        "位置: %s\n" +
                        "时间: %s\n" +
                        "置信度: %.2f%%\n" +
                        "描述: %s",
                majorPrefix, levelText, typeText,
                categoryLine,
                severityLine,
                alert.getCameraName(),
                alert.getLocation(),
                alert.getEventTime(),
                alert.getConfidence() != null ? alert.getConfidence().multiply(java.math.BigDecimal.valueOf(100)) : 0,
                alert.getDescription()
        );
    }

    private void sendDingTalkNotification(String content) {
        sendDingTalkNotification(content, false);
    }

    private void sendDingTalkNotification(String content, boolean isMajor) {
        try {
            NotificationConfig.DingTalk config = notificationConfig.getDingTalk();
            long timestamp = Instant.now().toEpochMilli();
            String sign = generateDingTalkSign(timestamp, config.getSecret());

            String url = config.getWebhook() + "&timestamp=" + timestamp + "&sign=" + sign;

            Map<String, Object> body = isMajor ? Map.of(
                    "msgtype", "text",
                    "text", Map.of("content", content),
                    "at", Map.of("isAtAll", true)
            ) : Map.of(
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
        sendWeChatNotification(content, false);
    }

    private void sendWeChatNotification(String content, boolean isMajor) {
        try {
            NotificationConfig.WeChat config = notificationConfig.getWeChat();

            String finalContent = isMajor ? "<font color=\"warning\">🚨 重大事故紧急告警</font>\n\n" + content : content;

            Map<String, Object> body = Map.of(
                    "msgtype", "markdown",
                    "markdown", Map.of(
                            "content", finalContent
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
        sendSmsNotification(alert, content, false);
    }

    private void sendSmsNotification(AlertEvent alert, String content, boolean isMajor) {
        try {
            NotificationConfig.Sms config = notificationConfig.getSms();

            String finalContent = isMajor ? "[重大事故] " + content : content;

            Map<String, Object> body = Map.of(
                    "apiKey", config.getApiKey(),
                    "templateId", isMajor && config.getEmergencyTemplateId() != null
                            ? config.getEmergencyTemplateId() : config.getTemplateId(),
                    "content", finalContent
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
