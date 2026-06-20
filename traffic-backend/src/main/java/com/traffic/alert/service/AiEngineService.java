package com.traffic.alert.service;

import com.alibaba.fastjson2.JSON;
import com.traffic.alert.config.AiEngineConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiEngineService {

    private final AiEngineConfig aiEngineConfig;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(5000))
            .build();

    public Map<String, Object> detectImage(MultipartFile imageFile) {
        try {
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            byte[] fileBytes = imageFile.getBytes();
            String fileName = imageFile.getOriginalFilename() != null ? imageFile.getOriginalFilename() : "image.jpg";

            StringBuilder bodyBuilder = new StringBuilder();
            bodyBuilder.append("--").append(boundary).append("\r\n");
            bodyBuilder.append("Content-Disposition: form-data; name=\"image\"; filename=\"").append(fileName).append("\"\r\n");
            bodyBuilder.append("Content-Type: ").append(imageFile.getContentType()).append("\r\n\r\n");

            byte[] prefix = bodyBuilder.toString().getBytes(StandardCharsets.UTF_8);
            byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

            byte[] body = new byte[prefix.length + fileBytes.length + suffix.length];
            System.arraycopy(prefix, 0, body, 0, prefix.length);
            System.arraycopy(fileBytes, 0, body, prefix.length, fileBytes.length);
            System.arraycopy(suffix, 0, body, prefix.length + fileBytes.length, suffix.length);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(aiEngineConfig.getBaseUrl() + aiEngineConfig.getDetectPath()))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(Duration.ofMillis(aiEngineConfig.getReadTimeout()))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return JSON.parseObject(response.body(), Map.class);
        } catch (Exception e) {
            log.error("调用AI图像检测失败: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    public Map<String, Object> startStreamDetection(Long cameraId, String streamUrl) {
        try {
            Map<String, Object> body = Map.of(
                    "cameraId", cameraId,
                    "streamUrl", streamUrl,
                    "fps", 2,
                    "enableTrack", true,
                    "enableEvent", true
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(aiEngineConfig.getBaseUrl() + aiEngineConfig.getStreamPath()))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(aiEngineConfig.getReadTimeout()))
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return JSON.parseObject(response.body(), Map.class);
        } catch (Exception e) {
            log.error("启动流检测失败: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    public Map<String, Object> stopStreamDetection(Long cameraId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(aiEngineConfig.getBaseUrl() + "/api/v1/detect/stream/" + cameraId + "/stop"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(aiEngineConfig.getReadTimeout()))
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return JSON.parseObject(response.body(), Map.class);
        } catch (Exception e) {
            log.error("停止流检测失败: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    public Map<String, Object> analyzeEvent(List<Map<String, Object>> trackData) {
        try {
            Map<String, Object> body = Map.of(
                    "tracks", trackData
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(aiEngineConfig.getBaseUrl() + aiEngineConfig.getEventPath()))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(aiEngineConfig.getReadTimeout()))
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return JSON.parseObject(response.body(), Map.class);
        } catch (Exception e) {
            log.error("事件分析失败: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    public boolean syncFenceToEngine(String action, Map<String, Object> fenceData) {
        try {
            String url = aiEngineConfig.getBaseUrl() + aiEngineConfig.getFencePath();
            HttpRequest.Builder requestBuilder;
            String jsonBody = JSON.toJSONString(fenceData);

            switch (action) {
                case "add" -> requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
                case "update" -> {
                    String fenceId = String.valueOf(fenceData.get("fenceId"));
                    requestBuilder = HttpRequest.newBuilder()
                            .uri(URI.create(url + "/" + fenceId))
                            .header("Content-Type", "application/json")
                            .PUT(HttpRequest.BodyPublishers.ofString(jsonBody));
                }
                case "delete" -> {
                    String fenceId = String.valueOf(fenceData.get("fenceId"));
                    requestBuilder = HttpRequest.newBuilder()
                            .uri(URI.create(url + "/" + fenceId))
                            .header("Content-Type", "application/json")
                            .DELETE();
                }
                default -> {
                    log.warn("未知围栏同步操作: {}", action);
                    return false;
                }
            }

            HttpResponse<String> response = httpClient.send(
                    requestBuilder.timeout(Duration.ofMillis(aiEngineConfig.getReadTimeout())).build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            log.info("同步围栏到AI引擎: action={}, fenceId={}, response={}", action, fenceData.get("fenceId"), response.statusCode());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.error("同步围栏到AI引擎失败: action={}, error={}", action, e.getMessage());
            return false;
        }
    }

    public boolean batchLoadFences(List<Map<String, Object>> fenceList) {
        try {
            String url = aiEngineConfig.getBaseUrl() + aiEngineConfig.getFencePath() + "/batch-load";
            String jsonBody = JSON.toJSONString(fenceList);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(aiEngineConfig.getReadTimeout()))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("批量加载围栏到AI引擎: count={}, response={}", fenceList.size(), response.statusCode());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.error("批量加载围栏到AI引擎失败: {}", e.getMessage());
            return false;
        }
    }

    public boolean loadFencesByCamera(Long cameraId) {
        try {
            String url = aiEngineConfig.getBaseUrl() + aiEngineConfig.getFencePath()
                    + "?camera_id=" + cameraId;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(aiEngineConfig.getReadTimeout()))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("按摄像头加载围栏到AI引擎: cameraId={}, response={}", cameraId, response.statusCode());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.error("按摄像头加载围栏到AI引擎失败: cameraId={}, error={}", cameraId, e.getMessage());
            return false;
        }
    }

    public boolean setCameraNeighbors(Long cameraId, List<String> neighborIds) {
        try {
            String url = aiEngineConfig.getBaseUrl() + "/api/v1/track-cross/neighbors/" + cameraId;
            String jsonBody = JSON.toJSONString(neighborIds);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(aiEngineConfig.getReadTimeout()))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("同步摄像头相邻关系到AI引擎: cameraId={}, neighbors={}, response={}",
                    cameraId, neighborIds.size(), response.statusCode());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.error("同步摄像头相邻关系到AI引擎失败: cameraId={}, error={}", cameraId, e.getMessage());
            return false;
        }
    }

    public boolean updateWeatherInfo(Long cameraId, String weatherType) {
        try {
            String url = aiEngineConfig.getBaseUrl() + "/api/v1/enhance/stream/" + cameraId + "/config";
            Map<String, Object> body = Map.of(
                    "external_weather", weatherType
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(aiEngineConfig.getReadTimeout()))
                    .PATCH(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("更新摄像头[{}]天气信息到AI引擎: weatherType={}, response={}",
                    cameraId, weatherType, response.statusCode());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.error("更新摄像头[{}]天气信息到AI引擎失败: error={}", cameraId, e.getMessage());
            return false;
        }
    }

    public boolean setRoadRegion(Long cameraId, String roadRegionPixel) {
        try {
            String url = aiEngineConfig.getBaseUrl() + "/api/v1/events/pedestrian-intrusion/road-region/" + cameraId;
            Map<String, Object> body = Map.of(
                    "cameraId", cameraId,
                    "roadRegionPixel", roadRegionPixel != null ? roadRegionPixel : ""
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(aiEngineConfig.getReadTimeout()))
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("同步摄像头[{}]行车道区域到AI引擎: response={}", cameraId, response.statusCode());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.error("同步摄像头[{}]行车道区域到AI引擎失败: error={}", cameraId, e.getMessage());
            return false;
        }
    }

    public boolean triggerPedestrianTracking(Long cameraId, String trackId, List<Long> neighborCameraIds) {
        try {
            String url = aiEngineConfig.getBaseUrl() + "/api/v1/track-cross/trigger-pedestrian-track";
            Map<String, Object> body = Map.of(
                    "sourceCameraId", cameraId,
                    "trackId", trackId != null ? trackId : "",
                    "neighborCameraIds", neighborCameraIds != null ? neighborCameraIds : List.of()
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(aiEngineConfig.getReadTimeout()))
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("触发行人跨摄像头追踪: cameraId={}, trackId={}, neighbors={}, response={}",
                    cameraId, trackId, neighborCameraIds, response.statusCode());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.error("触发行人跨摄像头追踪失败: cameraId={}, error={}", cameraId, e.getMessage());
            return false;
        }
    }

    public boolean syncConstructionPlan(Long cameraId, Map<String, Object> planConfig) {
        try {
            String url = aiEngineConfig.getBaseUrl() + "/api/v1/events/construction-plan/sync";
            Map<String, Object> body = Map.of(
                    "cameraId", cameraId,
                    "planConfig", planConfig != null ? planConfig : Map.of()
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(aiEngineConfig.getReadTimeout()))
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("同步施工计划配置到AI引擎: cameraId={}, planName={}, response={}",
                    cameraId, planConfig != null ? planConfig.get("plan_name") : "null", response.statusCode());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.error("同步施工计划配置到AI引擎失败: cameraId={}, error={}", cameraId, e.getMessage());
            return false;
        }
    }

    public boolean removeConstructionPlan(Long cameraId) {
        try {
            String url = aiEngineConfig.getBaseUrl() + "/api/v1/events/construction-plan/" + cameraId;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(aiEngineConfig.getReadTimeout()))
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("移除AI引擎施工计划配置: cameraId={}, response={}", cameraId, response.statusCode());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.error("移除AI引擎施工计划配置失败: cameraId={}, error={}", cameraId, e.getMessage());
            return false;
        }
    }
}
