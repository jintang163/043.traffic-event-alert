package com.traffic.alert.controller;

import com.traffic.alert.common.Result;
import com.traffic.alert.entity.Department;
import com.traffic.alert.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "部门管理")
@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @Operation(summary = "获取部门列表")
    @GetMapping("/list")
    public Result<List<Department>> list(@RequestParam(required = false) Integer deptType) {
        if (deptType != null) {
            return Result.success(departmentService.listByType(deptType));
        }
        return Result.success(departmentService.list());
    }

    @Operation(summary = "获取部门详情")
    @GetMapping("/{id}")
    public Result<Department> getById(@PathVariable Long id) {
        return Result.success(departmentService.getById(id));
    }

    @Operation(summary = "保存部门")
    @PostMapping
    public Result<Department> save(@RequestBody Department department) {
        return Result.success(departmentService.save(department));
    }

    @Operation(summary = "删除部门")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        departmentService.delete(id);
        return Result.success();
    }

    @Operation(summary = "查找最近的部门")
    @GetMapping("/nearest")
    public Result<Department> findNearest(
            @RequestParam BigDecimal longitude,
            @RequestParam BigDecimal latitude,
            @RequestParam(required = false) Integer deptType) {
        return Result.success(departmentService.findNearestDepartment(longitude, latitude, deptType));
    }
}
