package com.traffic.alert.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.traffic.alert.config.NotificationConfig;
import com.traffic.alert.entity.AlertEvent;
import com.traffic.alert.entity.NotifyChannel;
import com.traffic.alert.entity.NotifyLog;
import com.traffic.alert.entity.NotifyRule;
import com.traffic.alert.entity.NotifyTemplate;
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
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationConfig notificationConfig;
    private final NotifyChannelService notifyChannelService;
    private final NotifyTemplateService notifyTemplateService;
    private final NotifyRuleService notifyRuleService;
    private final NotifyLogService notifyLogService;
    private final OnDutyService onDutyService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public void sendAlertNotification(AlertEvent alert) {
        sendAlertNotification(alert, false);
    }

    public void sendAlertNotification(AlertEvent alert, boolean isMajor) {
        List<NotifyRule> rules = notifyRuleService.findMatchedRules(alert.getEventType(), alert.getEventLevel());
        if (rules.isEmpty()) {
            log.info("未匹配到通知规则，跳过推送: eventNo={}", alert.getEventNo());
            sendLegacyNotification(alert, isMajor);
            return;
        }

        for (NotifyRule rule : rules) {
            try {
                NotifyChannel channel = notifyChannelService.getById(rule.getChannelId());
                if (channel == null || channel.getEnabled() != 1) {
                    log.warn("通知渠道不可用: channelId={}, enabled={}",
                            rule.getChannelId(), channel != null ? channel.getEnabled() : null);
                    continue;
                }

                NotifyTemplate template = rule.getTemplateId() != null
                        ? notifyTemplateService.getById(rule.getTemplateId())
                        : notifyTemplateService.findBestTemplate(channel.getChannelType(), alert.getEventType(), alert.getEventLevel());

                String title = template != null && template.getTitleTemplate() != null
                        ? notifyTemplateService.renderTemplate(template.getTitleTemplate(), alert)
                        : "交通事件告警";
                String content = template != null && template.getContentTemplate() != null
                        ? notifyTemplateService.renderTemplate(template.getContentTemplate(), alert)
                        : buildAlertContent(alert, isMajor);

                List<String> recipients = resolveRecipients(rule);
                String recipientInfo = String.join(",", recipients);

                NotifyLog logEntry = new NotifyLog();
                logEntry.setAlertEventId(alert.getId());
                logEntry.setEventNo(alert.getEventNo());
                logEntry.setChannelId(channel.getId());
                logEntry.setChannelType(channel.getChannelType());
                logEntry.setTemplateId(template != null ? template.getId() : null);
                logEntry.setRecipientType(rule.getRecipientType());
                logEntry.setRecipientInfo(recipientInfo);
                logEntry.setTitle(title);
                logEntry.setContent(content);
                logEntry.setSendStatus(0);
                logEntry.setMaxRetry(notificationConfig.getRetry().getMaxRetries());

                notifyLogService.createLog(logEntry);

                dispatchNotification(logEntry, channel, rule);
            } catch (Exception e) {
                log.error("推送规则执行失败: ruleId={}, eventNo={}, error={}",
                        rule.getId(), alert.getEventNo(), e.getMessage());
            }
        }
    }

    private void dispatchNotification(NotifyLog logEntry, NotifyChannel channel, NotifyRule rule) {
        long startMs = System.currentTimeMillis();
        logEntry.setSendStatus(1);
        logEntry.setSendTime(LocalDateTime.now());
        notifyLogService.updateLog(logEntry);

        try {
            String responseBody;
            switch (channel.getChannelType()) {
                case "DINGTALK" -> responseBody = sendDingTalk(channel, logEntry, rule);
                case "SMS" -> responseBody = sendSms(channel, logEntry);
                case "VOICE" -> responseBody = sendVoice(channel, logEntry);
                case "WECHAT" -> responseBody = sendWeChat(channel, logEntry);
                default -> {
                    log.warn("未知渠道类型: {}", channel.getChannelType());
                    responseBody = "UNKNOWN_CHANNEL_TYPE";
                }
            }

            long costMs = System.currentTimeMillis() - startMs;
            logEntry.setSendStatus(2);
            logEntry.setSuccessTime(LocalDateTime.now());
            logEntry.setCostMs(costMs);
            logEntry.setResponseBody(truncate(responseBody, 2000));
            logEntry.setErrorMessage(null);
            log.info("通知推送成功: logNo={}, channel={}, cost={}ms",
                    logEntry.getLogNo(), channel.getChannelType(), costMs);
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startMs;
            logEntry.setSendStatus(3);
            logEntry.setCostMs(costMs);
            logEntry.setErrorMessage(truncate(e.getMessage(), 1000));
            logEntry.setRetryCount(logEntry.getRetryCount() + 1);
            if (logEntry.getRetryCount() < logEntry.getMaxRetry()) {
                long delaySec = (long) (notificationConfig.getRetry().getInitialDelaySeconds()
                        * Math.pow(notificationConfig.getRetry().getMultiplier(), logEntry.getRetryCount()));
                delaySec = Math.min(delaySec, notificationConfig.getRetry().getMaxDelaySeconds());
                logEntry.setNextRetryTime(LocalDateTime.now().plusSeconds(delaySec));
                log.warn("通知推送失败，将重试: logNo={}, retry={}/{}, nextRetry={}s",
                        logEntry.getLogNo(), logEntry.getRetryCount(), logEntry.getMaxRetry(), delaySec);
            } else {
                logEntry.setNextRetryTime(null);
                log.error("通知推送失败且已达最大重试次数: logNo={}", logEntry.getLogNo());
            }
        }
        notifyLogService.updateLog(logEntry);
    }

    public void retryNotify(NotifyLog logEntry) {
        NotifyChannel channel = notifyChannelService.getById(logEntry.getChannelId());
        if (channel == null || channel.getEnabled() != 1) {
            log.warn("重试渠道不可用: channelId={}", logEntry.getChannelId());
            return;
        }
        NotifyRule rule = new NotifyRule();
        rule.setAtAll(0);
        rule.setRecipientType(logEntry.getRecipientType());
        logEntry.setRetryCount(logEntry.getRetryCount() + 1);
        logEntry.setNextRetryTime(null);
        dispatchNotification(logEntry, channel, rule);
    }

    private List<String> resolveRecipients(NotifyRule rule) {
        return switch (rule.getRecipientType()) {
            case 1 -> onDutyService.getCurrentDuty().stream()
                    .map(d -> d.getPhone() != null ? d.getPhone() : d.getUserName())
                    .toList();
            case 4 -> List.of("ALL");
            default -> rule.getRecipientIds() != null
                    ? List.of(rule.getRecipientIds().split(","))
                    : List.of();
        };
    }

    private String sendDingTalk(NotifyChannel channel, NotifyLog logEntry, NotifyRule rule) {
        try {
            JSONObject config = JSON.parseObject(channel.getConfigJson());
            String webhook = config.getString("webhook");
            String secret = config.getString("secret");

            long timestamp = Instant.now().toEpochMilli();
            String sign = secret != null && !secret.isEmpty() ? generateDingTalkSign(timestamp, secret) : "";
            String url = webhook;
            if (!sign.isEmpty()) {
                url += "&timestamp=" + timestamp + "&sign=" + sign;
            } else if (!webhook.contains("sign=")) {
                // no sign needed
            }

            boolean atAll = rule != null && rule.getAtAll() != null && rule.getAtAll() == 1;
            Map<String, Object> body = atAll ? Map.of(
                    "msgtype", "text",
                    "text", Map.of("content", logEntry.getContent()),
                    "at", Map.of("isAtAll", true)
            ) : Map.of(
                    "msgtype", "text",
                    "text", Map.of("content", logEntry.getContent())
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception e) {
            throw new RuntimeException("钉钉通知发送失败: " + e.getMessage(), e);
        }
    }

    private String sendSms(NotifyChannel channel, NotifyLog logEntry) {
        try {
            JSONObject config = JSON.parseObject(channel.getConfigJson());
            String accessKeyId = config.getString("accessKeyId");
            String accessKeySecret = config.getString("accessKeySecret");
            String signName = config.getString("signName");
            String templateCode = config.getString("templateCode");
            String regionId = config.getString("regionId");

            if (accessKeyId != null && !accessKeyId.startsWith("YOUR_")) {
                return sendAliyunSms(accessKeyId, accessKeySecret, signName, templateCode,
                        logEntry.getRecipientInfo(), logEntry.getContent(), regionId);
            }

            String apiUrl = config.getString("apiUrl");
            if (apiUrl == null || apiUrl.isEmpty()) {
                apiUrl = "https://sms.example.com/api/send";
            }
            Map<String, Object> body = Map.of(
                    "apiKey", config.getString("apiKey") != null ? config.getString("apiKey") : "",
                    "templateId", templateCode != null ? templateCode : "",
                    "content", logEntry.getContent(),
                    "phones", logEntry.getRecipientInfo()
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception e) {
            throw new RuntimeException("短信通知发送失败: " + e.getMessage(), e);
        }
    }

    private String sendAliyunSms(String accessKeyId, String accessKeySecret, String signName,
                                  String templateCode, String phones, String content, String regionId) {
        try {
            String endpoint = String.format("https://dysmsapi.aliyuncs.com/?Action=SendSms" +
                    "&Format=JSON&Version=2017-05-25&AccessKeyId=%s&SignatureMethod=HMAC-SHA1" +
                    "&SignatureNonce=%s&SignatureVersion=1.0&SignatureVersion=1.0" +
                    "&PhoneNumbers=%s&SignName=%s&TemplateCode=%s&TemplateParam={\"content\":\"%s\"}" +
                    "&Timestamp=%s", accessKeyId, java.util.UUID.randomUUID().toString(),
                    phones, signName, templateCode,
                    java.net.URLEncoder.encode(content.length() > 100 ? content.substring(0, 100) : content, StandardCharsets.UTF_8),
                    java.net.URLEncoder.encode(Instant.now().toString(), StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception e) {
            throw new RuntimeException("阿里云短信发送失败: " + e.getMessage(), e);
        }
    }

    private String sendVoice(NotifyChannel channel, NotifyLog logEntry) {
        try {
            JSONObject config = JSON.parseObject(channel.getConfigJson());
            String accessKeyId = config.getString("accessKeyId");
            String accessKeySecret = config.getString("accessKeySecret");
            String ttsTemplateCode = config.getString("ttsTemplateCode");
            String calledShowNumber = config.getString("calledShowNumber");
            String regionId = config.getString("regionId");

            if (notificationConfig.getVoice() != null && notificationConfig.getVoice().isEnabled()) {
                return sendAliyunVoiceTts(accessKeyId, accessKeySecret, ttsTemplateCode,
                        calledShowNumber, logEntry.getRecipientInfo(), logEntry.getContent(), regionId);
            }

            log.info("语音TTS外呼(模拟): phones={}, content={}", logEntry.getRecipientInfo(),
                    logEntry.getContent().length() > 50 ? logEntry.getContent().substring(0, 50) + "..." : logEntry.getContent());
            return "{\"simulated\":true,\"message\":\"语音TTS未启用，模拟外呼成功\"}";
        } catch (Exception e) {
            throw new RuntimeException("语音通知发送失败: " + e.getMessage(), e);
        }
    }

    private String sendAliyunVoiceTts(String accessKeyId, String accessKeySecret, String ttsTemplateCode,
                                       String calledShowNumber, String phones, String content, String regionId) {
        try {
            String endpoint = String.format("https://dyvmsapi.aliyuncs.com/?Action=SingleCallByTts" +
                    "&Format=JSON&Version=2017-05-25&AccessKeyId=%s&SignatureMethod=HMAC-SHA1" +
                    "&SignatureNonce=%s&SignatureVersion=1.0" +
                    "&CalledNumber=%s&CalledShowNumber=%s&TtsCode=%s" +
                    "&TtsParam={\"content\":\"%s\"}" +
                    "&Timestamp=%s", accessKeyId, java.util.UUID.randomUUID().toString(),
                    phones, calledShowNumber, ttsTemplateCode,
                    java.net.URLEncoder.encode(content.length() > 100 ? content.substring(0, 100) : content, StandardCharsets.UTF_8),
                    java.net.URLEncoder.encode(Instant.now().toString(), StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception e) {
            throw new RuntimeException("阿里云语音TTS外呼失败: " + e.getMessage(), e);
        }
    }

    private String sendWeChat(NotifyChannel channel, NotifyLog logEntry) {
        try {
            JSONObject config = JSON.parseObject(channel.getConfigJson());
            String webhook = config.getString("webhook");

            Map<String, Object> body = Map.of(
                    "msgtype", "markdown",
                    "markdown", Map.of("content", logEntry.getContent())
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhook))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception e) {
            throw new RuntimeException("企业微信通知发送失败: " + e.getMessage(), e);
        }
    }

    private void sendLegacyNotification(AlertEvent alert, boolean isMajor) {
        String content = buildAlertContent(alert, isMajor);
        if (isMajor) {
            if (notificationConfig.getDingTalk().isEnabled()) {
                try { sendLegacyDingTalk(content, true); } catch (Exception e) { log.error("钉钉通知发送失败: {}", e.getMessage()); }
            }
            if (notificationConfig.getWeChat().isEnabled()) {
                try { sendLegacyWeChat(content, true); } catch (Exception e) { log.error("企微通知发送失败: {}", e.getMessage()); }
            }
            if (notificationConfig.getSms().isEnabled()) {
                try { sendLegacySms(alert, content, true); } catch (Exception e) { log.error("短信通知发送失败: {}", e.getMessage()); }
            }
        } else {
            if (notificationConfig.getDingTalk().isEnabled()) {
                try { sendLegacyDingTalk(content, false); } catch (Exception e) { log.error("钉钉通知发送失败: {}", e.getMessage()); }
            }
            if (notificationConfig.getWeChat().isEnabled()) {
                try { sendLegacyWeChat(content, false); } catch (Exception e) { log.error("企微通知发送失败: {}", e.getMessage()); }
            }
            if (notificationConfig.getSms().isEnabled()) {
                try { sendLegacySms(alert, content, false); } catch (Exception e) { log.error("短信通知发送失败: {}", e.getMessage()); }
            }
        }
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
            categoryLine = String.format("抛洒物子类: %s (编码: %s, 预设等级: L%d)%n",
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

    private void sendLegacyDingTalk(String content, boolean isMajor) {
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

    private void sendLegacyWeChat(String content, boolean isMajor) {
        try {
            NotificationConfig.WeChat config = notificationConfig.getWeChat();
            String finalContent = isMajor ? "<font color=\"warning\">🚨 重大事故紧急告警</font>\n\n" + content : content;
            Map<String, Object> body = Map.of(
                    "msgtype", "markdown",
                    "markdown", Map.of("content", finalContent)
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

    private void sendLegacySms(AlertEvent alert, String content, boolean isMajor) {
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

    private String generateDingTalkSign(long timestamp, String secret) throws Exception {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().encodeToString(signData);
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return null;
        return str.length() > maxLen ? str.substring(0, maxLen) : str;
    }
}
