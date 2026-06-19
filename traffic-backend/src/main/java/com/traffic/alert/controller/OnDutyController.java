package com.traffic.alert.controller;

import com.traffic.alert.common.PageResult;
import com.traffic.alert.common.Result;
import com.traffic.alert.dto.OnDutyQuery;
import com.traffic.alert.entity.OnDuty;
import com.traffic.alert.service.OnDutyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "值班排班管理")
@RestController
@RequestMapping("/api/on-duty")
@RequiredArgsConstructor
public class OnDutyController {

    private final OnDutyService onDutyService;

    @Operation(summary = "分页查询值班记录")
    @GetMapping("/page")
    public Result<PageResult<OnDuty>> page(OnDutyQuery query) {
        return Result.success(onDutyService.page(query));
    }

    @Operation(summary = "查询当前值班人员")
    @GetMapping("/current")
    public Result<List<OnDuty>> currentDuty() {
        return Result.success(onDutyService.getCurrentDuty());
    }

    @Operation(summary = "按日期查询值班")
    @GetMapping("/date/{date}")
    public Result<List<OnDuty>> getByDate(@PathVariable String date) {
        return Result.success(onDutyService.getDutyByDate(LocalDate.parse(date)));
    }

    @Operation(summary = "保存值班记录")
    @PostMapping
    public Result<OnDuty> save(@RequestBody OnDuty onDuty) {
        return Result.success(onDutyService.save(onDuty));
    }

    @Operation(summary = "删除值班记录")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        onDutyService.delete(id);
        return Result.success(null);
    }
}
