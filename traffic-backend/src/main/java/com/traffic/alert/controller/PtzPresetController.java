package com.traffic.alert.controller;

import com.traffic.alert.common.Result;
import com.traffic.alert.dto.PtzPresetRequest;
import com.traffic.alert.entity.PtzPreset;
import com.traffic.alert.service.PtzPresetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ptz/preset")
@RequiredArgsConstructor
public class PtzPresetController {

    private final PtzPresetService ptzPresetService;

    @GetMapping("/camera/{cameraId}")
    public Result<List<PtzPreset>> listByCamera(@PathVariable Long cameraId) {
        return Result.success(ptzPresetService.listByCamera(cameraId));
    }

    @GetMapping("/{id}")
    public Result<PtzPreset> getById(@PathVariable Long id) {
        return Result.success(ptzPresetService.getById(id));
    }

    @PostMapping
    public Result<PtzPreset> save(@RequestBody PtzPresetRequest request) {
        return Result.success(ptzPresetService.save(request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ptzPresetService.delete(id);
        return Result.success();
    }

    @GetMapping("/next-index/{cameraId}")
    public Result<Integer> getNextPresetIndex(@PathVariable Long cameraId) {
        return Result.success(ptzPresetService.getNextPresetIndex(cameraId));
    }
}
