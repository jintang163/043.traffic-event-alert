package com.traffic.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.alert.common.BusinessException;
import com.traffic.alert.common.PageResult;
import com.traffic.alert.dto.WorkOrderHandleRequest;
import com.traffic.alert.dto.WorkOrderQuery;
import com.traffic.alert.entity.WorkOrder;
import com.traffic.alert.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderService {

    private final WorkOrderMapper workOrderMapper;

    public WorkOrder getById(Long id) {
        return workOrderMapper.selectById(id);
    }

    public PageResult<WorkOrder> page(WorkOrderQuery query) {
        Page<WorkOrder> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();
        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
            wrapper.like(WorkOrder::getOrderNo, query.getKeyword())
                    .or().like(WorkOrder::getTitle, query.getKeyword());
        }
        if (query.getEventType() != null && !query.getEventType().isEmpty()) {
            wrapper.eq(WorkOrder::getEventType, query.getEventType());
        }
        if (query.getOrderLevel() != null) {
            wrapper.eq(WorkOrder::getOrderLevel, query.getOrderLevel());
        }
        if (query.getOrderStatus() != null) {
            wrapper.eq(WorkOrder::getOrderStatus, query.getOrderStatus());
        }
        if (query.getAssignDeptId() != null) {
            wrapper.eq(WorkOrder::getAssignDeptId, query.getAssignDeptId());
        }
        if (query.getAssignUserId() != null) {
            wrapper.eq(WorkOrder::getAssignUserId, query.getAssignUserId());
        }
        wrapper.orderByDesc(WorkOrder::getCreateTime);
        workOrderMapper.selectPage(page, wrapper);
        return PageResult.of(page.getTotal(), page.getRecords(), page.getCurrent(), page.getSize());
    }

    public WorkOrder save(WorkOrder workOrder) {
        if (workOrder.getId() == null) {
            workOrderMapper.insert(workOrder);
        } else {
            workOrderMapper.updateById(workOrder);
        }
        return workOrder;
    }

    public WorkOrder handleOrder(Long id, WorkOrderHandleRequest request) {
        WorkOrder order = getById(id);
        if (order == null) {
            throw new BusinessException("工单不存在");
        }

        if (request.getOrderStatus() != null) {
            order.setOrderStatus(request.getOrderStatus());
        }
        if (request.getHandleContent() != null) {
            order.setHandleContent(request.getHandleContent());
        }
        if (request.getHandleImages() != null) {
            order.setHandleImages(request.getHandleImages());
        }
        if (request.getRemark() != null) {
            order.setRemark(request.getRemark());
        }
        if (request.getActualStartTime() != null) {
            order.setActualStartTime(request.getActualStartTime());
        }
        if (request.getActualEndTime() != null) {
            order.setActualEndTime(request.getActualEndTime());
        }

        if (order.getOrderStatus() != null && order.getOrderStatus() == 2) {
            order.setActualEndTime(LocalDateTime.now());
        }

        workOrderMapper.updateById(order);
        log.info("工单处理: orderId={}, status={}", id, order.getOrderStatus());
        return order;
    }

    public void delete(Long id) {
        workOrderMapper.deleteById(id);
    }

    public List<WorkOrder> listByAlertEventId(Long alertEventId) {
        return workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getAlertEventId, alertEventId)
                .orderByDesc(WorkOrder::getCreateTime));
    }

    public Map<String, Object> getStatistics() {
        Long total = workOrderMapper.selectCount(new LambdaQueryWrapper<>());
        Long pending = workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getOrderStatus, 0));
        Long processing = workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getOrderStatus, 1));
        Long completed = workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getOrderStatus, 2));

        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        Long todayCreated = workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrder>()
                .ge(WorkOrder::getCreateTime, today));
        Long todayCompleted = workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrder>()
                .ge(WorkOrder::getActualEndTime, today));

        return Map.of(
                "total", total,
                "pending", pending,
                "processing", processing,
                "completed", completed,
                "todayCreated", todayCreated,
                "todayCompleted", todayCompleted
        );
    }
}
