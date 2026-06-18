package com.traffic.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.alert.common.BusinessException;
import com.traffic.alert.common.PageResult;
import com.traffic.alert.config.DeviceSdkConfig;
import com.traffic.alert.dto.CameraQuery;
import com.traffic.alert.dto.PtzControlRequest;
import com.traffic.alert.entity.Camera;
import com.traffic.alert.entity.CameraNeighbor;
import com.traffic.alert.entity.GeoFence;
import com.traffic.alert.mapper.CameraMapper;
import com.traffic.alert.mapper.CameraNeighborMapper;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CameraService {

    private final CameraMapper cameraMapper;
    private final CameraNeighborMapper cameraNeighborMapper;
    private final AiEngineService aiEngineService;
    private final GeoFenceService geoFenceService;
    private final DeviceSdkConfig deviceSdkConfig;

    private final HttpClient sdkHttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public Camera getById(Long id) {
        return cameraMapper.selectById(id);
    }

    public PageResult<Camera> page(CameraQuery query) {
        Page<Camera> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<Camera> wrapper = new LambdaQueryWrapper<>();
        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
            wrapper.like(Camera::getCameraName, query.getKeyword())
                    .or().like(Camera::getCameraCode, query.getKeyword());
        }
        if (query.getProtocol() != null) {
            wrapper.eq(Camera::getProtocol, query.getProtocol());
        }
        if (query.getManufacturer() != null) {
            wrapper.eq(Camera::getManufacturer, query.getManufacturer());
        }
        if (query.getRoadName() != null) {
            wrapper.like(Camera::getRoadName, query.getRoadName());
        }
        if (query.getStatus() != null) {
            wrapper.eq(Camera::getStatus, query.getStatus());
        }
        if (query.getOnlineStatus() != null) {
            wrapper.eq(Camera::getOnlineStatus, query.getOnlineStatus());
        }
        wrapper.orderByDesc(Camera::getCreateTime);
        cameraMapper.selectPage(page, wrapper);
        return PageResult.of(page.getTotal(), page.getRecords(), page.getCurrent(), page.getSize());
    }

    public List<Camera> list() {
        return cameraMapper.selectList(new LambdaQueryWrapper<Camera>()
                .eq(Camera::getStatus, 1)
                .orderByAsc(Camera::getRoadName, Camera::getDirection));
    }

    @Transactional
    public Camera save(Camera camera) {
        boolean isNew = camera.getId() == null;
        Camera before = isNew ? null : cameraMapper.selectById(camera.getId());

        if (isNew) {
            cameraMapper.insert(camera);
        } else {
            cameraMapper.updateById(camera);
        }

        if ("GB28181".equalsIgnoreCase(camera.getProtocol()) && camera.getStatus() != null && camera.getStatus() == 1) {
            registerGb28181Device(camera);
        }

        if (camera.getStatus() != null && camera.getStatus() == 1) {
            boolean needStartDetection = isNew || (before != null && (before.getStatus() == null || before.getStatus() == 0));
            if (needStartDetection) {
                try {
                    Map<String, Object> result = aiEngineService.startStreamDetection(camera.getId(), camera.getStreamUrl());
                    log.info("摄像头[{}]保存后自动启动AI检测: {}", camera.getId(), JSON.toJSONString(result));
                } catch (Exception e) {
                    log.warn("摄像头[{}]自动启动AI检测失败: {}", camera.getId(), e.getMessage());
                }
                try {
                    List<GeoFence> fences = geoFenceService.listByCamera(camera.getId());
                    if (!fences.isEmpty()) {
                        List<Map<String, Object>> fenceDataList = fences.stream().map(f -> Map.ofEntries(
                                Map.entry("fenceId", String.valueOf(f.getId())),
                                Map.entry("fenceCode", f.getFenceCode() != null ? f.getFenceCode() : ""),
                                Map.entry("fenceName", f.getFenceName() != null ? f.getFenceName() : ""),
                                Map.entry("fenceType", f.getFenceType() != null ? f.getFenceType() : 1),
                                Map.entry("cameraId", f.getCameraId() != null ? f.getCameraId() : 0),
                                Map.entry("polygonPointsPixel", f.getPolygonPointsPixel() != null ? f.getPolygonPointsPixel() : "[]"),
                                Map.entry("polygonPoints", f.getPolygonPoints() != null ? f.getPolygonPoints() : "[]"),
                                Map.entry("alertEnabled", f.getAlertEnabled() != null ? f.getAlertEnabled() : 1),
                                Map.entry("alertLevel", f.getAlertLevel() != null ? f.getAlertLevel() : 2),
                                Map.entry("detectTargetTypes", f.getDetectTargetTypes() != null ? f.getDetectTargetTypes() : ""),
                                Map.entry("staySeconds", f.getStaySeconds() != null ? f.getStaySeconds() : 0),
                                Map.entry("cooldownSeconds", f.getCooldownSeconds() != null ? f.getCooldownSeconds() : 60),
                                Map.entry("color", f.getColor() != null ? f.getColor() : "#ff4d4f")
                        )).toList();
                        aiEngineService.batchLoadFences(fenceDataList);
                        log.info("摄像头[{}]启动检测后同步 {} 个围栏到AI引擎", camera.getId(), fences.size());
                    }
                } catch (Exception e) {
                    log.warn("摄像头[{}]同步围栏到AI引擎失败: {}", camera.getId(), e.getMessage());
                }

                try {
                    List<CameraNeighbor> neighbors = cameraNeighborMapper.selectList(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CameraNeighbor>()
                                    .eq(CameraNeighbor::getCameraId, camera.getId())
                                    .orderByAsc(CameraNeighbor::getPriority)
                    );
                    if (!neighbors.isEmpty()) {
                        List<String> neighborIds = neighbors.stream()
                                .map(n -> String.valueOf(n.getNeighborCameraId()))
                                .toList();
                        aiEngineService.setCameraNeighbors(camera.getId(), neighborIds);
                        log.info("摄像头[{}]启动检测后同步 {} 个相邻摄像头到AI引擎", camera.getId(), neighbors.size());
                    }
                } catch (Exception e) {
                    log.warn("摄像头[{}]同步相邻摄像头到AI引擎失败: {}", camera.getId(), e.getMessage());
                }
            }
        } else if (!isNew && before != null && before.getStatus() == 1 && camera.getStatus() != null && camera.getStatus() == 0) {
            try {
                Map<String, Object> result = aiEngineService.stopStreamDetection(camera.getId());
                log.info("摄像头[{}]禁用后自动停止AI检测: {}", camera.getId(), JSON.toJSONString(result));
            } catch (Exception e) {
                log.warn("摄像头[{}]自动停止AI检测失败: {}", camera.getId(), e.getMessage());
            }
        }

        return camera;
    }

    @Transactional
    public void delete(Long id) {
        Camera camera = cameraMapper.selectById(id);
        if (camera != null && camera.getStatus() != null && camera.getStatus() == 1) {
            try {
                aiEngineService.stopStreamDetection(id);
            } catch (Exception e) {
                log.warn("删除摄像头[{}]停止AI检测失败: {}", id, e.getMessage());
            }
        }
        cameraMapper.deleteById(id);
    }

    public String getStreamUrl(Long id) {
        Camera camera = getById(id);
        if (camera == null) {
            throw new BusinessException("摄像头不存在");
        }
        return camera.getStreamUrl();
    }

    public Map<String, Object> ptzControl(Long id, PtzControlRequest request) {
        Camera camera = getById(id);
        if (camera == null) {
            throw new BusinessException("摄像头不存在");
        }
        if (camera.getPtzEnabled() == null || camera.getPtzEnabled() != 1) {
            throw new BusinessException("该摄像头不支持云台控制");
        }

        String manufacturer = camera.getManufacturer() != null ? camera.getManufacturer() : "";
        String command = request.getCommand();
        Integer speed = request.getSpeed() != null ? request.getSpeed() : 5;

        log.info("摄像头[{}][{}]云台控制: manufacturer={}, command={}, speed={}",
                id, camera.getCameraName(), manufacturer, command, speed);

        boolean sdkResult = false;
        try {
            if (deviceSdkConfig.isHikvisionEnabled() && manufacturer.contains("海康")) {
                sdkResult = ptzControlHikvision(camera, command, speed, request);
            } else if (deviceSdkConfig.isDahuaEnabled() && manufacturer.contains("大华")) {
                sdkResult = ptzControlDahua(camera, command, speed, request);
            } else if (deviceSdkConfig.getSdkProxyUrl() != null && !deviceSdkConfig.getSdkProxyUrl().isEmpty()) {
                sdkResult = ptzControlViaProxy(camera, command, speed, request);
            }
        } catch (Exception e) {
            log.error("摄像头[{}]云台SDK调用异常: {}", id, e.getMessage());
            sdkResult = false;
        }

        return Map.of(
                "success", true,
                "sdkExecuted", sdkResult,
                "cameraId", id,
                "command", command,
                "speed", speed,
                "manufacturer", manufacturer,
                "message", sdkResult ? "云台控制指令已通过SDK下发" : "云台控制指令已记录（SDK未配置或调用失败）"
        );
    }

    private boolean ptzControlHikvision(Camera camera, String command, int speed, PtzControlRequest request) {
        try {
            Map<String, Object> payload = Map.of(
                    "ip", extractIp(camera.getStreamUrl()),
                    "port", deviceSdkConfig.getHikvisionPort(),
                    "username", deviceSdkConfig.getHikvisionUsername(),
                    "password", deviceSdkConfig.getHikvisionPassword(),
                    "command", command,
                    "speed", speed,
                    "presetIndex", request.getPresetIndex() != null ? request.getPresetIndex() : 0,
                    "presetName", request.getPresetName() != null ? request.getPresetName() : "",
                    "action", request.getAction() != null ? request.getAction() : "start"
            );
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(deviceSdkConfig.getSdkProxyUrl() + "/api/hikvision/ptz"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(payload)))
                    .build();
            HttpResponse<String> resp = sdkHttpClient.send(req, HttpResponse.BodyHandlers.ofString());
            log.info("海康云台SDK返回: {}", resp.body());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            log.warn("海康云台SDK调用失败: {}", e.getMessage());
            return false;
        }
    }

    private boolean ptzControlDahua(Camera camera, String command, int speed, PtzControlRequest request) {
        try {
            Map<String, Object> payload = Map.of(
                    "ip", extractIp(camera.getStreamUrl()),
                    "port", deviceSdkConfig.getDahuaPort(),
                    "username", deviceSdkConfig.getDahuaUsername(),
                    "password", deviceSdkConfig.getDahuaPassword(),
                    "command", command,
                    "speed", speed,
                    "presetIndex", request.getPresetIndex() != null ? request.getPresetIndex() : 0,
                    "presetName", request.getPresetName() != null ? request.getPresetName() : "",
                    "action", request.getAction() != null ? request.getAction() : "start"
            );
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(deviceSdkConfig.getSdkProxyUrl() + "/api/dahua/ptz"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(payload)))
                    .build();
            HttpResponse<String> resp = sdkHttpClient.send(req, HttpResponse.BodyHandlers.ofString());
            log.info("大华云台SDK返回: {}", resp.body());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            log.warn("大华云台SDK调用失败: {}", e.getMessage());
            return false;
        }
    }

    private boolean ptzControlViaProxy(Camera camera, String command, int speed, PtzControlRequest request) {
        try {
            Map<String, Object> payload = Map.of(
                    "cameraId", camera.getId(),
                    "manufacturer", camera.getManufacturer() != null ? camera.getManufacturer() : "",
                    "streamUrl", camera.getStreamUrl() != null ? camera.getStreamUrl() : "",
                    "gbDeviceId", camera.getGbDeviceId() != null ? camera.getGbDeviceId() : "",
                    "command", command,
                    "speed", speed,
                    "presetIndex", request.getPresetIndex() != null ? request.getPresetIndex() : 0,
                    "presetName", request.getPresetName() != null ? request.getPresetName() : "",
                    "pan", request.getPan() != null ? request.getPan() : 0,
                    "tilt", request.getTilt() != null ? request.getTilt() : 0,
                    "zoom", request.getZoom() != null ? request.getZoom() : 1,
                    "action", request.getAction() != null ? request.getAction() : "start"
            );
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(deviceSdkConfig.getSdkProxyUrl() + "/api/device/ptz"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(payload)))
                    .build();
            HttpResponse<String> resp = sdkHttpClient.send(req, HttpResponse.BodyHandlers.ofString());
            log.info("设备代理云台SDK返回: {}", resp.body());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            log.warn("设备代理云台SDK调用失败: {}", e.getMessage());
            return false;
        }
    }

    private boolean registerGb28181Device(Camera camera) {
        if (!deviceSdkConfig.isGb28181Enabled()) {
            log.info("GB28181未启用，跳过设备注册: cameraId={}", camera.getId());
            return false;
        }
        try {
            Map<String, Object> payload = Map.of(
                    "cameraId", camera.getId(),
                    "deviceId", camera.getGbDeviceId() != null ? camera.getGbDeviceId() : "",
                    "cameraName", camera.getCameraName(),
                    "manufacturer", camera.getManufacturer() != null ? camera.getManufacturer() : "",
                    "longitude", camera.getLongitude() != null ? camera.getLongitude().doubleValue() : 0,
                    "latitude", camera.getLatitude() != null ? camera.getLatitude().doubleValue() : 0,
                    "sipServer", deviceSdkConfig.getGb28181SipServer(),
                    "sipPort", deviceSdkConfig.getGb28181SipPort(),
                    "sipId", deviceSdkConfig.getGb28181SipId(),
                    "sipDomain", deviceSdkConfig.getGb28181SipDomain(),
                    "sipPassword", deviceSdkConfig.getGb28181SipPassword(),
                    "mediaServer", deviceSdkConfig.getGb28181MediaServer(),
                    "mediaPort", deviceSdkConfig.getGb28181MediaPort()
            );
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(deviceSdkConfig.getSdkProxyUrl() + "/api/gb28181/register"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(payload)))
                    .build();
            HttpResponse<String> resp = sdkHttpClient.send(req, HttpResponse.BodyHandlers.ofString());
            log.info("GB28181设备注册返回: cameraId={}, resp={}", camera.getId(), resp.body());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            log.warn("GB28181设备注册失败: cameraId={}, err={}", camera.getId(), e.getMessage());
            return false;
        }
    }

    private String extractIp(String url) {
        if (url == null || url.isEmpty()) return "";
        try {
            int start = url.indexOf("://");
            if (start >= 0) {
                url = url.substring(start + 3);
            }
            int end = url.indexOf(':');
            if (end < 0) end = url.indexOf('/');
            if (end < 0) end = url.length();
            return url.substring(0, end);
        } catch (Exception e) {
            return "";
        }
    }

    public Map<String, Object> getStatistics() {
        Long total = cameraMapper.selectCount(new LambdaQueryWrapper<Camera>());
        Long online = cameraMapper.selectCount(new LambdaQueryWrapper<Camera>()
                .eq(Camera::getOnlineStatus, 1));
        Long offline = total - online;
        Long ptzEnabled = cameraMapper.selectCount(new LambdaQueryWrapper<Camera>()
                .eq(Camera::getPtzEnabled, 1));

        return Map.of(
                "total", total,
                "online", online,
                "offline", offline,
                "ptzEnabled", ptzEnabled
        );
    }

    public boolean gotoPreset(Long cameraId, Integer presetIndex) {
        Camera camera = getById(cameraId);
        if (camera == null || camera.getPtzEnabled() == null || camera.getPtzEnabled() != 1) {
            return false;
        }
        PtzControlRequest request = new PtzControlRequest();
        request.setCommand("gotoPreset");
        request.setPresetIndex(presetIndex);
        request.setSpeed(5);
        try {
            Map<String, Object> result = ptzControl(cameraId, request);
            return Boolean.TRUE.equals(result.get("sdkExecuted")) || Boolean.TRUE.equals(result.get("success"));
        } catch (Exception e) {
            log.warn("调用预置位失败: cameraId={}, presetIndex={}, err={}", cameraId, presetIndex, e.getMessage());
            return false;
        }
    }

    public boolean setPreset(Long cameraId, Integer presetIndex, String presetName) {
        Camera camera = getById(cameraId);
        if (camera == null || camera.getPtzEnabled() == null || camera.getPtzEnabled() != 1) {
            return false;
        }
        PtzControlRequest request = new PtzControlRequest();
        request.setCommand("setPreset");
        request.setPresetIndex(presetIndex);
        request.setPresetName(presetName);
        try {
            Map<String, Object> result = ptzControl(cameraId, request);
            return Boolean.TRUE.equals(result.get("sdkExecuted")) || Boolean.TRUE.equals(result.get("success"));
        } catch (Exception e) {
            log.warn("设置预置位失败: cameraId={}, presetIndex={}, err={}", cameraId, presetIndex, e.getMessage());
            return false;
        }
    }
}
