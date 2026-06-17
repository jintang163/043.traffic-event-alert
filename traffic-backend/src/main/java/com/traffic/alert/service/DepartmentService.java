package com.traffic.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.traffic.alert.entity.Department;
import com.traffic.alert.mapper.DepartmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentMapper departmentMapper;

    public Department getById(Long id) {
        return departmentMapper.selectById(id);
    }

    public List<Department> list() {
        return departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                .eq(Department::getStatus, 1)
                .orderByAsc(Department::getDeptType, Department::getDeptName));
    }

    public List<Department> listByType(Integer deptType) {
        return departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                .eq(Department::getStatus, 1)
                .eq(deptType != null, Department::getDeptType, deptType)
                .orderByAsc(Department::getDeptName));
    }

    public Department save(Department department) {
        if (department.getId() == null) {
            departmentMapper.insert(department);
        } else {
            departmentMapper.updateById(department);
        }
        return department;
    }

    public void delete(Long id) {
        departmentMapper.deleteById(id);
    }

    public Department findNearestDepartment(BigDecimal longitude, BigDecimal latitude, Integer deptType) {
        List<Department> departments = listByType(deptType);
        if (departments.isEmpty()) {
            return null;
        }

        return departments.stream()
                .filter(d -> d.getLongitude() != null && d.getLatitude() != null)
                .min(Comparator.comparingDouble(d -> calculateDistance(
                        longitude.doubleValue(),
                        latitude.doubleValue(),
                        d.getLongitude().doubleValue(),
                        d.getLatitude().doubleValue()
                )))
                .orElse(departments.isEmpty() ? null : departments.get(0));
    }

    private double calculateDistance(double lon1, double lat1, double lon2, double lat2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
