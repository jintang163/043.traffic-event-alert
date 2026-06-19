package com.traffic.alert.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.alert.common.PageResult;
import com.traffic.alert.dto.PolicePushQuery;
import com.traffic.alert.entity.AlertEvent;
import com.traffic.alert.entity.PlateRecognition;
import com.traffic.alert.entity.PolicePush;
import com.traffic.alert.entity.PoliceSystemConfig;
import com.traffic.alert.mapper.PolicePushMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PolicePushService {

    private final PolicePushMapper mapper;
    private final PoliceSystemConfigService configService;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public PolicePush getById(Long id) {
        return mapper.selectById(id);
    }

    public List<PolicePush> listByAlertEventId(Long alertEventId) {
        return mapper.selectList(new LambdaQueryWrapper<PolicePush>()
                .eq(PolicePush::getAlertEventId, alertEventId)
                .orderByDesc(PolicePush::getPushTime));
    }

    public PageResult<PolicePush> page(PolicePushQuery query) {
        Page<PolicePush> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<PolicePush> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getEventNo())) {
            wrapper.eq(PolicePush::getEventNo, query.getEventNo());
        }
        if (StringUtils.hasText(query.getPlateNumber())) {
            wrapper.like(PolicePush::getPlateNumber, query.getPlateNumber());
        }
        if (query.getPushStatus() != null) {
            wrapper.eq(PolicePush::getPushStatus, query.getPushStatus());
        }
        if (StringUtils.hasText(query.getPushTarget())) {
            wrapper.eq(PolicePush::getPushTarget, query.getPushTarget());
        }
        if (StringUtils.hasText(query.getStartTime())) {
            wrapper.ge(PolicePush::getEventTime, LocalDateTime.parse(query.getStartTime()));
        }
        if (StringUtils.hasText(query.getEndTime())) {
            wrapper.le(PolicePush::getEventTime, LocalDateTime.parse(query.getEndTime()));
        }
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(PolicePush::getPlateNumber, query.getKeyword())
                    .or().like(PolicePush::getEventNo, query.getKeyword()));
        }
        wrapper.orderByDesc(PolicePush::getPushTime);
        mapper.selectPage(page, wrapper);
        return PageResult.of(page.getTotal(), page.getRecords(), page.getCurrent(), query.getSize());
    }

    public PolicePush save(PolicePush entity) {
        if (entity.getId() == null) {
            if (!StringUtils.hasText(entity.getPushNo())) {
                entity.setPushNo("PP" + LocalDateTime.now().format(FORMATTER)
                        + String.format("%04d", (int) (Math.random() * 10000)));
            }
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        return entity;
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }

    public List<PolicePush> pushForReverseEvent(AlertEvent event, List<PlateRecognition> plates) {
        List<PoliceSystemConfig> configs = configService.listEnabled();
        if (configs.isEmpty()) {
            log.info("无启用的交警系统推送配置，跳过推送: eventNo={}", event.getEventNo());
            return List.of();
        }
        if (plates == null || plates.isEmpty()) {
            log.warn("逆行事件未识别到车牌，跳过交警系统推送: eventNo={}", event.getEventNo());
            return List.of();
        }
        java.util.ArrayList<PolicePush> results = new java.util.ArrayList<>();
        for (PoliceSystemConfig cfg : configs) {
            for (PlateRecognition plate : plates) {
                PolicePush push = buildPushRecord(event, plate, cfg);
                save(push);
                doPush(push, cfg);
                results.add(push);
            }
        }
        return results;
    }

    private PolicePush buildPushRecord(AlertEvent event, PlateRecognition plate, PoliceSystemConfig cfg) {
        PolicePush p = new PolicePush();
        p.setAlertEventId(event.getId());
        p.setEventNo(event.getEventNo());
        p.setPlateRecognitionId(plate.getId());
        p.setEventType(event.getEventType());
        p.setEventLevel(event.getEventLevel());
        p.setPlateNumber(plate.getPlateNumber());
        p.setPlateColor(plate.getPlateColor());
        p.setVehicleType(plate.getVehicleType());
        p.setLocation(event.getLocation());
        p.setCameraId(event.getCameraId());
        p.setCameraName(event.getCameraName());
        p.setLongitude(event.getLongitude());
        p.setLatitude(event.getLatitude());
        p.setEventTime(event.getEventTime());
        p.setPushTarget(cfg.getSystemCode());
        p.setPushStatus(0);
        p.setRetryCount(0);
        p.setMaxRetry(cfg.getRetryMax() != null ? cfg.getRetryMax() : 5);
        p.setPushBody(buildPushBody(event, plate, cfg).toJSONString());
        return p;
    }

    private JSONObject buildPushBody(AlertEvent event, PlateRecognition plate, PoliceSystemConfig cfg) {
        JSONObject j = new JSONObject();
        j.put("eventNo", event.getEventNo());
        j.put("eventType", event.getEventType());
        j.put("eventLevel", event.getEventLevel());
        j.put("eventTime", event.getEventTime() != null ? event.getEventTime().toString() : null);
        j.put("description", event.getDescription());
        j.put("plateNumber", plate.getPlateNumber());
        j.put("plateColor", plate.getPlateColor());
        j.put("vehicleColor", plate.getVehicleColor());
        j.put("vehicleType", plate.getVehicleType());
        j.put("confidence", plate.getConfidence());
        j.put("sceneType", plate.getSceneType());
        j.put("cameraId", event.getCameraId());
        j.put("cameraName", event.getCameraName());
        j.put("location", event.getLocation());
        j.put("longitude", event.getLongitude());
        j.put("latitude", event.getLatitude());
        j.put("bbox", plate.getBboxX1() != null ?
                new int[]{plate.getBboxX1(), plate.getBboxY1(), plate.getBboxX2(), plate.getBboxY2()} : null);
        j.put("source", "traffic-ai-engine");
        j.put("timestamp", System.currentTimeMillis());
        return j;
    }

    public void doPush(PolicePush push, PoliceSystemConfig cfg) {
        long startMs = System.currentTimeMillis();
        push.setPushStatus(1);
        push.setPushTime(LocalDateTime.now());
        save(push);
        try {
            if (cfg.getPushUrl() == null || cfg.getPushUrl().isEmpty()) {
                throw new RuntimeException("push_url 未配置");
            }

            int timeoutSec = cfg.getTimeoutSeconds() != null ? cfg.getTimeoutSeconds() : 10;
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(timeoutSec))
                    .build();

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(cfg.getPushUrl()))
                    .timeout(java.time.Duration.ofSeconds(timeoutSec))
                    .header("Content-Type", "application/json;charset=UTF-8");

            if ("TOKEN".equalsIgnoreCase(cfg.getAuthType()) && StringUtils.hasText(cfg.getAuthToken())) {
                reqBuilder.header("Authorization", "Bearer " + cfg.getAuthToken());
            } else if ("BASIC".equalsIgnoreCase(cfg.getAuthType())) {
                String username = cfg.getBasicUsername() != null ? cfg.getBasicUsername() : "";
                String password = cfg.getBasicPassword() != null ? cfg.getBasicPassword() : "";
                String basic = username + ":" + password;
                String encoded = Base64.getEncoder().encodeToString(basic.getBytes(StandardCharsets.UTF_8));
                reqBuilder.header("Authorization", "Basic " + encoded);
            }
            reqBuilder.POST(HttpRequest.BodyPublishers.ofString(push.getPushBody(), StandardCharsets.UTF_8));

            HttpResponse<String> resp = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            long cost = System.currentTimeMillis() - startMs;
            push.setCostMs(cost);
            push.setResponseBody(truncate(resp.body(), 2000));

            if (resp.statusCode() >= 200 && resp.statusCode() < 300 && isSuccessBody(resp.body())) {
                push.setPushStatus(2);
                push.setSuccessTime(LocalDateTime.now());
                push.setErrorMessage(null);
                push.setNextRetryTime(null);
                log.info("交警系统推送成功: pushNo={}, target={}, plate={}, cost={}ms, status={}",
                        push.getPushNo(), cfg.getSystemCode(), push.getPlateNumber(), cost, resp.statusCode());
            } else {
                throw new RuntimeException("HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 300));
            }
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - startMs;
            push.setCostMs(cost);
            push.setPushStatus(3);
            push.setErrorMessage(truncate(e.getMessage(), 1000));
            push.setRetryCount(push.getRetryCount() + 1);
            if (push.getRetryCount() < push.getMaxRetry()) {
                int initSec = cfg.getRetryInitialSeconds() != null ? cfg.getRetryInitialSeconds() : 10;
                double mult = cfg.getRetryMultiplier() != null ? cfg.getRetryMultiplier().doubleValue() : 2.0;
                int maxSec = cfg.getRetryMaxSeconds() != null ? cfg.getRetryMaxSeconds() : 300;
                long delay = (long) (initSec * Math.pow(mult, push.getRetryCount()));
                delay = Math.min(delay, maxSec);
                push.setNextRetryTime(LocalDateTime.now().plusSeconds(delay));
                log.warn("交警系统推送失败，将在{}秒后重试: pushNo={}, target={}, plate={}, retry={}/{}, err={}",
                        delay, push.getPushNo(), cfg.getSystemCode(), push.getPlateNumber(),
                        push.getRetryCount(), push.getMaxRetry(), e.getMessage());
            } else {
                push.setNextRetryTime(null);
                log.error("交警系统推送失败且已达最大重试: pushNo={}, target={}, plate={}, err={}",
                        push.getPushNo(), cfg.getSystemCode(), push.getPlateNumber(), e.getMessage());
            }
        }
        save(push);
    }

    private boolean isSuccessBody(String body) {
        if (!StringUtils.hasText(body)) return true;
        try {
            JSONObject j = JSON.parseObject(body);
            for (String key : List.of("code", "errcode", "status", "resultCode")) {
                if (j.containsKey(key)) {
                    Object v = j.get(key);
                    if (v instanceof Number n) {
                        return n.intValue() == 0 || n.intValue() == 200;
                    }
                    if (v instanceof String s) {
                        return "0".equals(s) || "200".equalsIgnoreCase(s) || "OK".equalsIgnoreCase(s) || "SUCCESS".equalsIgnoreCase(s);
                    }
                }
            }
            if (j.containsKey("success")) {
                return Boolean.TRUE.equals(j.getBoolean("success"));
            }
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void retryPendingPushes() {
        List<PolicePush> pending = findPendingRetry();
        if (pending.isEmpty()) return;
        log.info("交警系统推送重试扫描: {} 条待重试", pending.size());
        for (PolicePush p : pending) {
            try {
                PoliceSystemConfig cfg = configService.getByCode(p.getPushTarget());
                if (cfg == null) {
                    log.warn("重试推送配置不存在，跳过: pushNo={}, target={}", p.getPushNo(), p.getPushTarget());
                    continue;
                }
                doPush(p, cfg);
            } catch (Exception e) {
                log.error("重试交警推送异常: pushNo={}, err={}", p.getPushNo(), e.getMessage());
            }
        }
    }

    public List<PolicePush> findPendingRetry() {
        return mapper.selectList(new LambdaQueryWrapper<PolicePush>()
                .in(PolicePush::getPushStatus, 1, 3)
                .apply("retry_count < max_retry")
                .and(w -> w.isNull(PolicePush::getNextRetryTime)
                        .or().le(PolicePush::getNextRetryTime, LocalDateTime.now()))
                .orderByAsc(PolicePush::getNextRetryTime)
                .last("LIMIT 100"));
    }

    public PolicePush manualRetry(Long id) {
        PolicePush p = getById(id);
        if (p == null) throw new com.traffic.alert.common.BusinessException("推送记录不存在");
        PoliceSystemConfig cfg = configService.getByCode(p.getPushTarget());
        if (cfg == null) throw new com.traffic.alert.common.BusinessException("推送目标配置不存在");
        p.setNextRetryTime(null);
        doPush(p, cfg);
        return p;
    }

    private static String truncate(String str, int max) {
        if (str == null) return null;
        return str.length() > max ? str.substring(0, max) : str;
    }

    public Map<String, Object> getStatistics() {
        long total = mapper.selectCount(new LambdaQueryWrapper<>());
        long success = mapper.selectCount(new LambdaQueryWrapper<PolicePush>().eq(PolicePush::getPushStatus, 2));
        long failed = mapper.selectCount(new LambdaQueryWrapper<PolicePush>().eq(PolicePush::getPushStatus, 3));
        long pending = mapper.selectCount(new LambdaQueryWrapper<PolicePush>().in(PolicePush::getPushStatus, 0, 1));
        return Map.of("total", total, "success", success, "failed", failed, "pending", pending);
    }
}
