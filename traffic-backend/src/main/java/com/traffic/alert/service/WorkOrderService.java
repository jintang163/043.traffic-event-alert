package com.traffic.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.alert.common.BusinessException;
import com.traffic.alert.common.PageResult;
import com.traffic.alert.dto.WorkOrderHandleRequest;
import com.traffic.alert.dto.WorkOrderQuery;
import com.traffic.alert.entity.Department;
import com.traffic.alert.entity.User;
import com.traffic.alert.entity.WorkOrder;
import com.traffic.alert.mapper.DepartmentMapper;
import com.traffic.alert.mapper.UserMapper;
import com.traffic.alert.mapper.WorkOrderMapper;
import com.traffic.alert.rule.dto.RuleExecuteResult;
import com.traffic.alert.rule.entity.RuleBranch;
import com.traffic.alert.rule.service.RuleEngineService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderService {

    private final WorkOrderMapper workOrderMapper;
    private final DepartmentMapper departmentMapper;
    private final UserMapper userMapper;
    private final RuleEngineService ruleEngineService;

    private static final String WORK_ORDER_ASSIGN_RULE = "WORK_ORDER_ASSIGN";

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
            autoAssignByRule(workOrder);
            workOrderMapper.insert(workOrder);
            log.info("工单创建并自动分派: orderNo={}, assignDept={}, assignUser={}",
                    workOrder.getOrderNo(), workOrder.getAssignDeptName(), workOrder.getAssignUserName());
        } else {
            workOrderMapper.updateById(workOrder);
        }
        return workOrder;
    }

    private void autoAssignByRule(WorkOrder workOrder) {
        try {
            Map<String, Object> formData = new HashMap<>();
            if (workOrder.getOrderLevel() != null) {
                formData.put("urgency", workOrder.getOrderLevel());
            }
            formData.put("title", workOrder.getTitle() != null ? workOrder.getTitle() : "");
            formData.put("category", workOrder.getEventType() != null ? workOrder.getEventType() : "");
            formData.put("amount", BigDecimal.ZERO);

            Map<String, Object> systemVars = new HashMap<>();
            Long createUserId = 1L;
            systemVars.put("userId", createUserId);

            Map<String, Object> businessData = new HashMap<>();
            businessData.put("eventType", workOrder.getEventType());
            businessData.put("eventLevel", workOrder.getOrderLevel());
            businessData.put("orderLevel", workOrder.getOrderLevel());

            RuleExecuteResult result = ruleEngineService.executeAndApply(
                    WORK_ORDER_ASSIGN_RULE, formData, systemVars, businessData);

            if (Boolean.TRUE.equals(result.getSuccess())
                    && result.getMatchedBranches() != null
                    && !result.getMatchedBranches().isEmpty()) {

                for (RuleBranch branch : result.getMatchedBranches()) {
                    applyBranchAction(workOrder, branch);
                }
                log.info("工单规则路由执行成功: ruleCode={}, matched={}, executionId={}",
                        result.getRuleCode(),
                        result.getMatchedBranches().stream().map(RuleBranch::getBranchName).toList(),
                        result.getExecutionId());
            } else {
                log.info("工单规则路由无匹配分支，使用默认分派: executionId={}, error={}",
                        result.getExecutionId(), result.getErrorMessage());
            }
        } catch (Exception e) {
            log.warn("工单自动分派规则执行失败，使用默认策略: {}", e.getMessage());
        }
    }

    private void applyBranchAction(WorkOrder workOrder, RuleBranch branch) {
        String actionType = branch.getActionType();
        String actionTarget = branch.getActionTarget();
        String actionParams = branch.getActionParams();

        log.info("执行分支动作: branch={}, actionType={}, target={}", branch.getBranchName(), actionType, actionTarget);

        if ("APPROVAL".equalsIgnoreCase(actionType) || "ASSIGN".equalsIgnoreCase(actionType)) {
            if (actionTarget != null && !actionTarget.isEmpty()) {
                resolveAssignTarget(workOrder, actionTarget);
            }
        }

        if (actionParams != null && !actionParams.isEmpty()) {
            try {
                Map<String, Object> params = JSON.parseObject(actionParams, new TypeReference<Map<String, Object>>() {});
                if (params.containsKey("deptId") || params.containsKey("deptName")) {
                    Object deptId = params.get("deptId");
                    Object deptName = params.get("deptName");
                    if (deptId != null && workOrder.getAssignDeptId() == null) {
                        workOrder.setAssignDeptId(Long.valueOf(deptId.toString()));
                    }
                    if (deptName != null && workOrder.getAssignDeptName() == null) {
                        workOrder.setAssignDeptName(deptName.toString());
                    }
                }
                if (params.containsKey("userId") || params.containsKey("userName")) {
                    Object userId = params.get("userId");
                    Object userName = params.get("userName");
                    if (userId != null && workOrder.getAssignUserId() == null) {
                        workOrder.setAssignUserId(Long.valueOf(userId.toString()));
                    }
                    if (userName != null && workOrder.getAssignUserName() == null) {
                        workOrder.setAssignUserName(userName.toString());
                    }
                }
                if (params.containsKey("orderLevel")) {
                    workOrder.setOrderLevel(Integer.valueOf(params.get("orderLevel").toString()));
                }
            } catch (Exception e) {
                log.warn("解析分支动作参数失败: {}", e.getMessage());
            }
        }
    }

    private void resolveAssignTarget(WorkOrder workOrder, String target) {
        if (target == null || target.isEmpty()) {
            return;
        }
        switch (target.toLowerCase()) {
            case "director":
            case "总监":
                workOrder.setAssignDeptName("总监办公室");
                workOrder.setOrderLevel(3);
                findAndSetDeptByName(workOrder, "总监");
                break;
            case "manager":
            case "经理":
                workOrder.setAssignDeptName("高速交警一大队");
                workOrder.setOrderLevel(2);
                findAndSetDeptByName(workOrder, "交警");
                break;
            case "team_lead":
            case "组长":
            case "普通":
            default:
                workOrder.setAssignDeptName("高速养护一队");
                workOrder.setOrderLevel(1);
                findAndSetDeptByName(workOrder, "养护");
                break;
        }
    }

    private void findAndSetDeptByName(WorkOrder workOrder, String keyword) {
        try {
            List<Department> depts = departmentMapper.selectList(
                    new LambdaQueryWrapper<Department>().like(Department::getDeptName, keyword));
            if (!depts.isEmpty()) {
                Department dept = depts.get(0);
                workOrder.setAssignDeptId(dept.getId());
                workOrder.setAssignDeptName(dept.getDeptName());
            }
        } catch (Exception e) {
            log.warn("查询部门失败: {}", e.getMessage());
        }
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
