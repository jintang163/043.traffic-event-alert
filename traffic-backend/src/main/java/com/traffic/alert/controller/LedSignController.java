package com.traffic.alert.controller;

import com.traffic.alert.common.Result;
import com.traffic.alert.service.LedSignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@Tag(name = "LED情报板管理")
@RestController
@RequestMapping("/api/led-sign")
@RequiredArgsConstructor
public class LedSignController {

    private final LedSignService ledSignService;

    @Operation(summary = "获取LED情报板状态")
    @GetMapping("/status/{cameraId}")
    public Result<LedSignService.LedSignStatus> getStatus(@PathVariable Long cameraId) {
        return Result.success(ledSignService.getStatus(cameraId));
    }

    @Operation(summary = "显示行人警示")
    @PostMapping("/pedestrian-warning/{cameraId}")
    public Result<Boolean> displayPedestrianWarning(@PathVariable Long cameraId) {
        boolean success = ledSignService.displayPedestrianWarning(cameraId);
        return Result.success(success);
    }

    @Operation(summary = "显示自定义消息")
    @PostMapping("/display/{cameraId}")
    public Result<Boolean> displayMessage(
            @PathVariable Long cameraId,
            @RequestBody Map<String, Object> params) {
        String message = (String) params.get("message");
        String color = params.get("color") != null ? (String) params.get("color") : "YELLOW";
        boolean isAlert = params.get("isAlert") != null && (Boolean) params.get("isAlert");
        int displaySeconds = params.get("displaySeconds") != null
                ? ((Number) params.get("displaySeconds")).intValue()
                : 30;
        boolean success = ledSignService.displayMessage(cameraId, message, color, isAlert, displaySeconds);
        return Result.success(success);
    }

    @Operation(summary = "恢复默认显示")
    @PostMapping("/restore/{cameraId}")
    public Result<Boolean> restoreDefault(@PathVariable Long cameraId) {
        boolean success = ledSignService.restoreDefault(cameraId);
        return Result.success(success);
    }

    @Operation(summary = "设置默认消息")
    @PutMapping("/default-message/{cameraId}")
    public Result<Boolean> setDefaultMessage(
            @PathVariable Long cameraId,
            @RequestBody Map<String, String> params) {
        String message = params.get("message");
        boolean success = ledSignService.setDefaultMessage(cameraId, message);
        return Result.success(success);
    }

    @Operation(summary = "设置亮度")
    @PutMapping("/brightness/{cameraId}")
    public Result<Boolean> setBrightness(
            @PathVariable Long cameraId,
            @RequestBody Map<String, Integer> params) {
        int brightness = params.getOrDefault("brightness", 100);
        boolean success = ledSignService.setBrightness(cameraId, brightness);
        return Result.success(success);
    }
}
