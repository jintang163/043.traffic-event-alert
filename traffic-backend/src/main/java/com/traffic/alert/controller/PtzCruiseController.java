package com.traffic.alert.controller;

import com.traffic.alert.common.Result;
import com.traffic.alert.dto.PtzCruiseRequest;
import com.traffic.alert.entity.PtzCruise;
import com.traffic.alert.service.PtzCruiseService;
import com.traffic.alert.vo.PtzCruiseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ptz/cruise")
@RequiredArgsConstructor
public class PtzCruiseController {

    private final PtzCruiseService ptzCruiseService;

    @GetMapping("/camera/{cameraId}")
    public Result<List<PtzCruise>> listByCamera(@PathVariable Long cameraId) {
        return Result.success(ptzCruiseService.listByCamera(cameraId));
    }

    @GetMapping("/{id}")
    public Result<PtzCruiseVO> getDetail(@PathVariable Long id) {
        return Result.success(ptzCruiseService.getDetail(id));
    }

    @PostMapping
    public Result<PtzCruise> save(@RequestBody PtzCruiseRequest request) {
        return Result.success(ptzCruiseService.save(request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ptzCruiseService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/start")
    public Result<Void> startCruise(@PathVariable Long id) {
        ptzCruiseService.startCruise(id);
        return Result.success();
    }

    @PostMapping("/{id}/stop")
    public Result<Void> stopCruise(@PathVariable Long id) {
        ptzCruiseService.stopCruise(id);
        return Result.success();
    }

    @GetMapping("/status/{cameraId}")
    public Result<Boolean> isCruising(@PathVariable Long cameraId) {
        return Result.success(ptzCruiseService.isCruising(cameraId));
    }
}
