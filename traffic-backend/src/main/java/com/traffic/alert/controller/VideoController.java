package com.traffic.alert.controller;

import com.traffic.alert.common.PageResult;
import com.traffic.alert.common.Result;
import com.traffic.alert.dto.VideoClipQuery;
import com.traffic.alert.entity.VideoClip;
import com.traffic.alert.service.VideoClipService;
import com.traffic.alert.service.VideoRecordingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "事件视频管理")
@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoClipService videoClipService;
    private final VideoRecordingService videoRecordingService;

    @Operation(summary = "分页查询视频片段")
    @GetMapping("/page")
    public Result<PageResult<VideoClip>> page(VideoClipQuery query) {
        return Result.success(videoClipService.page(query));
    }

    @Operation(summary = "获取视频详情")
    @GetMapping("/{id}")
    public Result<VideoClip> getById(@PathVariable Long id) {
        return Result.success(videoClipService.getById(id));
    }

    @Operation(summary = "按事件ID获取关联视频")
    @GetMapping("/by-event/{eventId}")
    public Result<List<VideoClip>> listByEvent(@PathVariable Long eventId) {
        return Result.success(videoClipService.listByEvent(eventId));
    }

    @Operation(summary = "删除视频片段")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        videoClipService.delete(id);
        return Result.success();
    }

    @Operation(summary = "检查ffmpeg可用性")
    @GetMapping("/ffmpeg-status")
    public Result<Map<String, Object>> ffmpegStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("ffmpegAvailable", videoRecordingService.isFfmpegAvailable());
        return Result.success(result);
    }

    @Operation(summary = "获取存储统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        Map<String, Object> result = new HashMap<>();
        result.put("todayCount", videoClipService.getTodayCount());
        result.put("storageBytes", videoClipService.getStorageBytes());
        result.put("storageMB", videoClipService.getStorageBytes() / (1024.0 * 1024.0));
        return Result.success(result);
    }
}
