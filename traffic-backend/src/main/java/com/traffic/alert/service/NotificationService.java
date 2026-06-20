package com.traffic.alert.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.traffic.alert.config.NotificationConfig;
import com.traffic.alert.entity.AlertEvent;
import com.traffic.alert.entity.Department;
import com.traffic.alert.entity.NotifyChannel;
import com.traffic.alert.entity.NotifyLog;
import com.traffic.alert.entity.NotifyRule;
import com.traffic.alert.entity.NotifyTemplate;
import com.traffic.alert.entity.OnDuty;
import com.traffic.alert.entity.User;
import com.traffic.alert.enums.DebrisCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final UserService userService;
    private final DepartmentService departmentService;
    private final PlateRecognitionService plateRecognitionService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public void sendAlertNotification(AlertEvent alert) {
        sendAlertNotification(alert, false);
    }

    public void sendAlertNotification(AlertEvent alert, boolean isMajor) {
        List<NotifyRule> rules = notifyRuleService.findMatchedRules(alert.getEventType(), alert.getEventLevel());
        if (rules.isEmpty()) {
            log.info("未匹配到通知规则，走 legacy 推送路径: eventNo={}", alert.getEventNo());
            sendLegacyNotification(alert, isMajor);
            return;
        }

        for (NotifyRule rule : rules) {
            try {
                NotifyChannel channel = notifyChannelService.getById(rule.getChannelId());
                if (channel == null || channel.getEnabled() != 1) {
                    log.warn("通知渠道不可用: ruleId={}, channelId={}, enabled={}",
                            rule.getId(), rule.getChannelId(), channel != null ? channel.getEnabled() : null);
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

                List<Recipient> recipients = resolveRecipients(rule);
                if (recipients.isEmpty()) {
                    log.warn("接收人列表为空，跳过推送: ruleId={}, recipientType={}", rule.getId(), rule.getRecipientType());
                    continue;
                }

                dispatchPerChannel(alert, rule, channel, template, title, content, recipients);
            } catch (Exception e) {
                log.error("推送规则执行失败: ruleId={}, eventNo={}, error={}",
                        rule.getId(), alert.getEventNo(), e.getMessage(), e);
            }
        }
    }

    private void dispatchPerChannel(AlertEvent alert, NotifyRule rule, NotifyChannel channel,
                                    NotifyTemplate template, String title, String content, List<Recipient> recipients) {
        Map<String, String> plateVars = buildPlateVars(alert);
        if (template != null && !plateVars.isEmpty()) {
            if (template.getTitleTemplate() != null) {
                String t = notifyTemplateService.renderTemplate(template.getTitleTemplate(), alert, plateVars);
                if (t != null) title = t;
            }
            if (template.getContentTemplate() != null) {
                String c = notifyTemplateService.renderTemplate(template.getContentTemplate(), alert, plateVars);
                if (c != null) content = c;
            }
        }
        String channelType = channel.getChannelType();
        if ("SMS".equals(channelType) || "VOICE".equals(channelType)) {
            for (Recipient r : recipients) {
                if (r.phone == null || r.phone.isEmpty()) {
                    log.warn("接收人无手机号，跳过{}{}: name={}", channelType, r.id, r.name);
                    continue;
                }
                NotifyLog logEntry = buildLogEntry(alert, rule, channel, template, title, content,
                        r.id + "/" + r.name, r.phone);
                notifyLogService.createLog(logEntry);
                dispatchNotification(logEntry, channel, rule);
            }
        } else {
            String recipientInfo = recipients.stream()
                    .map(r -> r.name + (r.phone != null ? ":" + r.phone : ""))
                    .collect(Collectors.joining(","));
            NotifyLog logEntry = buildLogEntry(alert, rule, channel, template, title, content, recipientInfo, recipientInfo);
            notifyLogService.createLog(logEntry);
            dispatchNotification(logEntry, channel, rule);
        }
    }

    private NotifyLog buildLogEntry(AlertEvent alert, NotifyRule rule, NotifyChannel channel,
                                    NotifyTemplate template, String title, String content,
                                    String recipientLabel, String recipientSendTarget) {
        NotifyLog logEntry = new NotifyLog();
        logEntry.setAlertEventId(alert.getId());
        logEntry.setEventNo(alert.getEventNo());
        logEntry.setChannelId(channel.getId());
        logEntry.setChannelType(channel.getChannelType());
        logEntry.setTemplateId(template != null ? template.getId() : null);
        logEntry.setRecipientType(rule.getRecipientType());
        logEntry.setRecipientInfo(truncate(recipientSendTarget != null ? recipientSendTarget : recipientLabel, 512));
        logEntry.setTitle(title);
        logEntry.setContent(content);
        logEntry.setSendStatus(0);
        logEntry.setMaxRetry(notificationConfig.getRetry().getMaxRetries());
        return logEntry;
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
                    throw new RuntimeException("未知渠道类型: " + channel.getChannelType());
                }
            }
            long costMs = System.currentTimeMillis() - startMs;
            logEntry.setSendStatus(2);
            logEntry.setSuccessTime(LocalDateTime.now());
            logEntry.setCostMs(costMs);
            logEntry.setResponseBody(truncate(responseBody, 2000));
            logEntry.setErrorMessage(null);
            log.info("通知推送成功: logNo={}, channel={}, to={}, cost={}ms",
                    logEntry.getLogNo(), channel.getChannelType(), logEntry.getRecipientInfo(), costMs);
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
                log.warn("通知推送失败，将重试: logNo={}, retry={}/{}, nextRetry={}s, err={}",
                        logEntry.getLogNo(), logEntry.getRetryCount(), logEntry.getMaxRetry(), delaySec, e.getMessage());
            } else {
                logEntry.setNextRetryTime(null);
                log.error("通知推送失败且已达最大重试次数: logNo={}, err={}", logEntry.getLogNo(), e.getMessage());
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

    private List<Recipient> resolveRecipients(NotifyRule rule) {
        List<Recipient> list = new ArrayList<>();
        int type = rule.getRecipientType() != null ? rule.getRecipientType() : 1;
        try {
            switch (type) {
                case 1 -> {
                    List<OnDuty> duties = onDutyService.getCurrentDuty();
                    for (OnDuty d : duties) {
                        String phone = d.getPhone();
                        String name = d.getUserName();
                        if (phone == null || phone.isEmpty()) {
                            if (d.getUserId() != null) {
                                User u = userService.getById(d.getUserId());
                                if (u != null) {
                                    phone = u.getPhone();
                                    if (name == null || name.isEmpty()) name = u.getNickname();
                                }
                            }
                        }
                        list.add(new Recipient(d.getUserId(), name, phone));
                    }
                }
                case 2 -> {
                    List<Long> deptIds = parseIds(rule.getRecipientIds());
                    if (!deptIds.isEmpty()) {
                        List<User> users = userService.listByDeptIds(deptIds);
                        for (User u : users) {
                            list.add(new Recipient(u.getId(), u.getNickname() != null ? u.getNickname() : u.getUsername(), u.getPhone()));
                        }
                        for (Long deptId : deptIds) {
                            Department d = departmentService.getById(deptId);
                            if (d != null && d.getContactPhone() != null && !d.getContactPhone().isEmpty()) {
                                list.add(new Recipient(-deptId, d.getContactPerson(), d.getContactPhone()));
                            }
                        }
                    }
                }
                case 3 -> {
                    List<Long> userIds = parseIds(rule.getRecipientIds());
                    if (!userIds.isEmpty()) {
                        for (User u : userService.listByIds(userIds)) {
                            list.add(new Recipient(u.getId(), u.getNickname() != null ? u.getNickname() : u.getUsername(), u.getPhone()));
                        }
                    }
                }
                case 4 -> {
                    for (User u : userService.listAllEnabled()) {
                        list.add(new Recipient(u.getId(), u.getNickname() != null ? u.getNickname() : u.getUsername(), u.getPhone()));
                    }
                }
                default -> log.warn("未知 recipientType={}", type);
            }
        } catch (Exception e) {
            log.error("解析接收人失败: recipientType={}, ids={}, err={}",
                    rule.getRecipientType(), rule.getRecipientIds(), e.getMessage());
        }
        Map<String, Recipient> dedup = new LinkedHashMap<>();
        for (Recipient r : list) {
            if (r.phone != null && !r.phone.isEmpty()) {
                dedup.putIfAbsent(r.phone, r);
            } else if (r.id != null) {
                dedup.putIfAbsent("U:" + r.id, r);
            }
        }
        return new ArrayList<>(dedup.values());
    }

    private List<Long> parseIds(String s) {
        if (s == null || s.isEmpty()) return Collections.emptyList();
        List<Long> list = new ArrayList<>();
        for (String p : s.split(",")) {
            p = p.trim();
            if (!p.isEmpty()) {
                try { list.add(Long.parseLong(p)); } catch (Exception ignored) {}
            }
        }
        return list;
    }

    private static class Recipient {
        final Long id;
        final String name;
        final String phone;
        Recipient(Long id, String name, String phone) {
            this.id = id;
            this.name = name;
            this.phone = phone;
        }
    }

    // ========================== 钉钉 ==========================
    private String sendDingTalk(NotifyChannel channel, NotifyLog logEntry, NotifyRule rule) {
        try {
            JSONObject config = JSON.parseObject(channel.getConfigJson());
            String webhook = config.getString("webhook");
            if (webhook == null || webhook.isEmpty()) {
                throw new RuntimeException("钉钉渠道未配置 webhook");
            }
            String secret = config.getString("secret");

            long timestamp = Instant.now().toEpochMilli();
            String sign = secret != null && !secret.isEmpty() ? generateDingTalkSign(timestamp, secret) : "";
            String url = webhook;
            if (!sign.isEmpty()) {
                url += (webhook.contains("?") ? "&" : "?") + "timestamp=" + timestamp + "&sign=" + sign;
            }

            boolean atAll = rule != null && rule.getAtAll() != null && rule.getAtAll() == 1;
            Map<String, Object> body = new HashMap<>();
            body.put("msgtype", "text");
            body.put("text", Map.of("content", logEntry.getContent()));
            if (atAll) {
                body.put("at", Map.of("isAtAll", true));
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String respBody = response.body();
            checkDingTalkResponse(respBody);
            return respBody;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("钉钉通知发送失败: " + e.getMessage(), e);
        }
    }

    private void checkDingTalkResponse(String body) {
        try {
            JSONObject j = JSON.parseObject(body);
            Integer errcode = j.getInteger("errcode");
            String errmsg = j.getString("errmsg");
            if (errcode != null && errcode != 0) {
                throw new RuntimeException("钉钉返回错误 errcode=" + errcode + ", errmsg=" + errmsg);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("钉钉响应解析失败: " + e.getMessage() + ", body=" + body, e);
        }
    }

    // ========================== 阿里云短信 ==========================
    private String sendSms(NotifyChannel channel, NotifyLog logEntry) {
        try {
            JSONObject config = JSON.parseObject(channel.getConfigJson());
            String accessKeyId = config.getString("accessKeyId");
            String accessKeySecret = config.getString("accessKeySecret");
            String signName = config.getString("signName");
            String templateCode = config.getString("templateCode");
            String regionId = config.getString("regionId");
            if (regionId == null || regionId.isEmpty()) regionId = "cn-hangzhou";

            if (accessKeyId == null || accessKeyId.isEmpty() || accessKeyId.startsWith("YOUR_")
                    || accessKeySecret == null || accessKeySecret.isEmpty() || accessKeySecret.startsWith("YOUR_")) {
                log.warn("阿里云短信未配置有效 AK，改为本地模拟发送: to={}", logEntry.getRecipientInfo());
                return simulateSms(logEntry);
            }

            Map<String, String> params = buildAliyunRpcCommonParams(accessKeyId, regionId);
            params.put("Action", "SendSms");
            params.put("Version", "2017-05-25");
            params.put("PhoneNumbers", logEntry.getRecipientInfo());
            params.put("SignName", signName != null ? signName : "交通告警");
            params.put("TemplateCode", templateCode != null ? templateCode : "SMS_000000000");
            JSONObject tplParam = new JSONObject();
            String smsText = toSmsText(logEntry.getContent());
            tplParam.put("content", smsText);
            params.put("TemplateParam", JSON.toJSONString(tplParam));

            String query = buildAliyunRpcQuery(params, accessKeySecret, "GET", "/");
            String endpoint = "https://dysmsapi.aliyuncs.com/?" + query;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String respBody = response.body();
            checkAliyunResponse(respBody, "短信");
            return respBody;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("短信通知发送失败: " + e.getMessage(), e);
        }
    }

    // ========================== 阿里云语音 TTS 外呼 ==========================
    private String sendVoice(NotifyChannel channel, NotifyLog logEntry) {
        try {
            JSONObject config = JSON.parseObject(channel.getConfigJson());
            String accessKeyId = config.getString("accessKeyId");
            String accessKeySecret = config.getString("accessKeySecret");
            String ttsTemplateCode = config.getString("ttsTemplateCode");
            String calledShowNumber = config.getString("calledShowNumber");
            String regionId = config.getString("regionId");
            if (regionId == null || regionId.isEmpty()) regionId = "cn-hangzhou";

            if (accessKeyId == null || accessKeyId.isEmpty() || accessKeyId.startsWith("YOUR_")
                    || accessKeySecret == null || accessKeySecret.isEmpty() || accessKeySecret.startsWith("YOUR_")) {
                NotificationConfig.Voice vc = notificationConfig.getVoice();
                if (vc != null && vc.getAccessKeyId() != null && !vc.getAccessKeyId().startsWith("YOUR_")) {
                    accessKeyId = vc.getAccessKeyId();
                    accessKeySecret = vc.getAccessKeySecret();
                    if (ttsTemplateCode == null) ttsTemplateCode = vc.getTtsTemplateCode();
                    if (calledShowNumber == null) calledShowNumber = vc.getCalledShowNumber();
                }
            }

            if (accessKeyId == null || accessKeyId.isEmpty() || accessKeyId.startsWith("YOUR_")
                    || accessKeySecret == null || accessKeySecret.isEmpty() || accessKeySecret.startsWith("YOUR_")) {
                log.warn("阿里云语音未配置有效 AK，改为本地模拟外呼: to={}", logEntry.getRecipientInfo());
                return simulateVoice(logEntry);
            }

            Map<String, String> params = buildAliyunRpcCommonParams(accessKeyId, regionId);
            params.put("Action", "SingleCallByTts");
            params.put("Version", "2017-05-25");
            params.put("CalledNumber", logEntry.getRecipientInfo());
            if (calledShowNumber != null && !calledShowNumber.isEmpty()) {
                params.put("CalledShowNumber", calledShowNumber);
            }
            params.put("TtsCode", ttsTemplateCode != null ? ttsTemplateCode : "TTS_000000000");
            JSONObject ttsParam = new JSONObject();
            ttsParam.put("content", toSmsText(logEntry.getContent()));
            params.put("TtsParam", JSON.toJSONString(ttsParam));
            params.put("PlayTimes", "2");

            String query = buildAliyunRpcQuery(params, accessKeySecret, "GET", "/");
            String endpoint = "https://dyvmsapi.aliyuncs.com/?" + query;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String respBody = response.body();
            checkAliyunResponse(respBody, "语音");
            return respBody;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("语音通知发送失败: " + e.getMessage(), e);
        }
    }

    // ========================== 企微 ==========================
    private String sendWeChat(NotifyChannel channel, NotifyLog logEntry) {
        try {
            JSONObject config = JSON.parseObject(channel.getConfigJson());
            String webhook = config.getString("webhook");
            if (webhook == null || webhook.isEmpty()) {
                throw new RuntimeException("企微渠道未配置 webhook");
            }

            Map<String, Object> body = Map.of(
                    "msgtype", "markdown",
                    "markdown", Map.of("content", logEntry.getContent())
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhook))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String respBody = response.body();
            checkWeChatResponse(respBody);
            return respBody;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("企业微信通知发送失败: " + e.getMessage(), e);
        }
    }

    private void checkWeChatResponse(String body) {
        try {
            JSONObject j = JSON.parseObject(body);
            Integer errcode = j.getInteger("errcode");
            String errmsg = j.getString("errmsg");
            if (errcode != null && errcode != 0) {
                throw new RuntimeException("企微返回错误 errcode=" + errcode + ", errmsg=" + errmsg);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("企微响应解析失败: " + e.getMessage() + ", body=" + body, e);
        }
    }

    // ========================== 阿里云 RPC v3 签名 ==========================
    private Map<String, String> buildAliyunRpcCommonParams(String accessKeyId, String regionId) {
        Map<String, String> p = new TreeMap<>();
        p.put("Format", "JSON");
        p.put("Version", "");
        p.put("AccessKeyId", accessKeyId);
        p.put("SignatureMethod", "HMAC-SHA1");
        p.put("SignatureNonce", UUID.randomUUID().toString().replace("-", ""));
        p.put("SignatureVersion", "1.0");
        p.put("Timestamp", DateTimeFormatter.ISO_INSTANT
                .format(LocalDateTime.now().atZone(ZoneId.of("UTC")).toInstant()));
        p.put("RegionId", regionId);
        return p;
    }

    private String buildAliyunRpcQuery(Map<String, String> params, String accessKeySecret, String method, String path) throws Exception {
        String canonicalizedQuery = params.entrySet().stream()
                .map(e -> percentEncode(e.getKey()) + "=" + percentEncode(e.getValue()))
                .collect(Collectors.joining("&"));
        String stringToSign = method + "&" + percentEncode(path) + "&" + percentEncode(canonicalizedQuery);
        String signature = hmacSha1Base64(stringToSign, accessKeySecret + "&");
        return canonicalizedQuery + "&Signature=" + percentEncode(signature);
    }

    private static String percentEncode(String s) {
        if (s == null) return "";
        try {
            String e = URLEncoder.encode(s, StandardCharsets.UTF_8.name());
            return e.replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static String hmacSha1Base64(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private void checkAliyunResponse(String body, String name) {
        try {
            JSONObject j = JSON.parseObject(body);
            String code = j.getString("Code");
            String message = j.getString("Message");
            String bizId = j.getString("BizId");
            String requestId = j.getString("RequestId");
            if (code != null && !"OK".equalsIgnoreCase(code)) {
                throw new RuntimeException(name + "返回错误 Code=" + code + ", Message=" + message
                        + (requestId != null ? ", RequestId=" + requestId : ""));
            }
            log.debug("阿里云{}响应 OK, BizId={}, RequestId={}", name, bizId, requestId);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(name + "响应解析失败: " + e.getMessage() + ", body=" + body, e);
        }
    }

    private String simulateSms(NotifyLog logEntry) {
        log.info("[模拟短信] to={}, text={}", logEntry.getRecipientInfo(),
                toSmsText(logEntry.getContent()));
        JSONObject j = new JSONObject();
        j.put("RequestId", UUID.randomUUID().toString());
        j.put("BizId", "SIM" + UUID.randomUUID().toString().substring(0, 8));
        j.put("Code", "OK");
        j.put("Message", "模拟发送成功");
        return JSON.toJSONString(j);
    }

    private String simulateVoice(NotifyLog logEntry) {
        log.info("[模拟语音外呼] to={}, tts={}", logEntry.getRecipientInfo(),
                toSmsText(logEntry.getContent()));
        JSONObject j = new JSONObject();
        j.put("RequestId", UUID.randomUUID().toString());
        j.put("CallId", "SIM" + UUID.randomUUID().toString().substring(0, 8));
        j.put("Code", "OK");
        j.put("Message", "模拟外呼成功");
        return JSON.toJSONString(j);
    }

    private static String toSmsText(String content) {
        if (content == null) return "";
        String t = content.replaceAll("\\s+", " ").trim();
        return t.length() > 120 ? t.substring(0, 120) : t;
    }

    // ========================== Legacy ==========================
    private void sendLegacyNotification(AlertEvent alert, boolean isMajor) {
        String content = buildAlertContent(alert, isMajor);
        if (notificationConfig.getDingTalk().isEnabled()) {
            try { sendLegacyDingTalk(content, isMajor); } catch (Exception e) { log.error("钉钉通知发送失败: {}", e.getMessage()); }
        }
        if (notificationConfig.getWeChat().isEnabled()) {
            try { sendLegacyWeChat(content, isMajor); } catch (Exception e) { log.error("企微通知发送失败: {}", e.getMessage()); }
        }
        if (notificationConfig.getSms().isEnabled()) {
            try { sendLegacySms(alert, content, isMajor); } catch (Exception e) { log.error("短信通知发送失败: {}", e.getMessage()); }
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
                    try {
                        yield "抛洒物-" + DebrisCategory.of(alert.getDebrisCategory()).getLabel();
                    } catch (Exception ignored) {}
                }
                yield "路面抛洒物";
            }
            case "PEDESTRIAN_INTRUSION" -> "行人闯入";
            default -> alert.getEventType();
        };

        String categoryLine = "";
        if ("DEBRIS".equals(alert.getEventType()) && alert.getDebrisCategory() != null) {
            try {
                DebrisCategory dc = DebrisCategory.of(alert.getDebrisCategory());
                categoryLine = String.format("抛洒物子类: %s (编码: %s, 预设等级: L%d)%n",
                        dc.getLabel(), dc.getCode(), dc.getDefaultLevel());
            } catch (Exception ignored) {}
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
                alert.getConfidence() != null ? alert.getConfidence().multiply(java.math.BigDecimal.valueOf(100)) : java.math.BigDecimal.ZERO,
                alert.getDescription() != null ? alert.getDescription() : ""
        );
    }

    private void sendLegacyDingTalk(String content, boolean isMajor) {
        try {
            NotificationConfig.DingTalk config = notificationConfig.getDingTalk();
            long timestamp = Instant.now().toEpochMilli();
            String sign = generateDingTalkSign(timestamp, config.getSecret());
            String url = config.getWebhook() + (config.getWebhook().contains("?") ? "&" : "?")
                    + "timestamp=" + timestamp + "&sign=" + sign;
            Map<String, Object> body = new HashMap<>();
            body.put("msgtype", "text");
            body.put("text", Map.of("content", content));
            if (isMajor) {
                body.put("at", Map.of("isAtAll", true));
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body), StandardCharsets.UTF_8))
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
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("企业微信通知发送结果: {}", response.body());
        } catch (Exception e) {
            log.error("企业微信通知发送失败: {}", e.getMessage());
        }
    }

    private void sendLegacySms(AlertEvent alert, String content, boolean isMajor) {
        log.info("legacy 短信暂未启用真实调用，按手机号逐个模拟外呼; isMajor={}, content={}",
                isMajor, toSmsText(content));
    }

    private static String generateDingTalkSign(long timestamp, String secret) throws Exception {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return percentEncode(Base64.getEncoder().encodeToString(signData));
    }

    private static String truncate(String str, int maxLen) {
        if (str == null) return null;
        return str.length() > maxLen ? str.substring(0, maxLen) : str;
    }

    private Map<String, String> buildPlateVars(AlertEvent alert) {
        Map<String, String> vars = new HashMap<>();
        if (alert == null || alert.getId() == null) return vars;
        try {
            List<com.traffic.alert.entity.PlateRecognition> plates = plateRecognitionService.listByAlertEventId(alert.getId());
            if (plates.isEmpty()) return vars;
            com.traffic.alert.entity.PlateRecognition best = plates.get(0);
            String joined = plates.stream()
                    .map(p -> (p.getPlateNumber() != null ? p.getPlateNumber() : ""))
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .collect(Collectors.joining("、"));
            vars.put("plateNumber", best.getPlateNumber() != null ? best.getPlateNumber() : "");
            vars.put("plateNumbers", joined);
            vars.put("plateColor", best.getPlateColor() != null ? best.getPlateColor() : "");
            vars.put("vehicleType", best.getVehicleType() != null ? best.getVehicleType() : "");
            vars.put("vehicleColor", best.getVehicleColor() != null ? best.getVehicleColor() : "");
            vars.put("plateConfidence", best.getConfidence() != null
                    ? String.valueOf(best.getConfidence().multiply(new java.math.BigDecimal(100))) : "");
            vars.put("sceneType", best.getSceneType() != null ? best.getSceneType() : "");
            vars.put("plateCount", String.valueOf(plates.size()));
        } catch (Exception e) {
            log.debug("buildPlateVars failed: {}", e.getMessage());
        }
        return vars;
    }
}
