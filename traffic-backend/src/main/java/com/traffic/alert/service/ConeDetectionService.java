package com.traffic.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.alert.common.BusinessException;
import com.traffic.alert.common.PageResult;
import com.traffic.alert.dto.ConeDetectionQuery;
import com.traffic.alert.entity.ConeDetectionRecord;
import com.traffic.alert.entity.ConstructionPlan;
import com.traffic.alert.mapper.ConeDetectionRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConeDetectionService {

    private final ConeDetectionRecordMapper coneDetectionRecordMapper;
    private final ConstructionPlanService constructionPlanService;

    private static final DateTimeFormatter RECORD_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public ConeDetectionRecord getById(Long id) {
        return coneDetectionRecordMapper.selectById(id);
    }

    public PageResult<ConeDetectionRecord> page(ConeDetectionQuery query) {
        Page<ConeDetectionRecord> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<ConeDetectionRecord> wrapper = new LambdaQueryWrapper<>();

        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
            wrapper.like(ConeDetectionRecord::getRecordNo, query.getKeyword())
                    .or().like(ConeDetectionRecord::getPlanName, query.getKeyword());
        }
        if (query.getPlanId() != null) {
            wrapper.eq(ConeDetectionRecord::getPlanId, query.getPlanId());
        }
        if (query.getCameraId() != null) {
            wrapper.eq(ConeDetectionRecord::getCameraId, query.getCameraId());
        }
        if (query.getIsCompliant() != null) {
            wrapper.eq(ConeDetectionRecord::getIsCompliant, query.getIsCompliant());
        }
        if (query.getAlertTriggered() != null) {
            wrapper.eq(ConeDetectionRecord::getAlertTriggered, query.getAlertTriggered());
        }
        if (query.getDetectionTimeStart() != null) {
            wrapper.ge(ConeDetectionRecord::getDetectionTime, query.getDetectionTimeStart());
        }
        if (query.getDetectionTimeEnd() != null) {
            wrapper.le(ConeDetectionRecord::getDetectionTime, query.getDetectionTimeEnd());
        }

        wrapper.orderByDesc(ConeDetectionRecord::getDetectionTime);
        coneDetectionRecordMapper.selectPage(page, wrapper);
        return PageResult.of(page.getTotal(), page.getRecords(), page.getCurrent(), page.getSize());
    }

    public List<ConeDetectionRecord> listByPlan(Long planId) {
        LambdaQueryWrapper<ConeDetectionRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConeDetectionRecord::getPlanId, planId)
                .orderByDesc(ConeDetectionRecord::getDetectionTime);
        return coneDetectionRecordMapper.selectList(wrapper);
    }

    public ConeDetectionRecord getLatestByPlan(Long planId) {
        LambdaQueryWrapper<ConeDetectionRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConeDetectionRecord::getPlanId, planId)
                .orderByDesc(ConeDetectionRecord::getDetectionTime)
                .last("LIMIT 1");
        return coneDetectionRecordMapper.selectOne(wrapper);
    }

    @Transactional
    public ConeDetectionRecord save(ConeDetectionRecord record) {
        if (record.getDetectionTime() == null) {
            record.setDetectionTime(LocalDateTime.now());
        }

        if (record.getPlanId() != null) {
            ConstructionPlan plan = constructionPlanService.getById(record.getPlanId());
            if (plan != null) {
                record.setPlanCode(plan.getPlanCode());
                record.setPlanName(plan.getPlanName());
                if (record.getCameraId() == null && plan.getCameraId() != null) {
                    record.setCameraId(plan.getCameraId());
                    record.setCameraName(plan.getCameraName());
                }
                if (record.getStandardConeCount() == null && plan.getStandardConeCount() != null) {
                    record.setStandardConeCount(plan.getStandardConeCount());
                }
            }
        }

        calculateCompliance(record);

        if (record.getId() == null) {
            String recordNo = record.getRecordNo();
            if (recordNo == null || recordNo.isEmpty()) {
                recordNo = "CONE" + LocalDateTime.now().format(RECORD_NO_FORMATTER) +
                        String.format("%04d", (int) (Math.random() * 10000));
                record.setRecordNo(recordNo);
            }
            if (record.getIsCompliant() == null) {
                record.setIsCompliant(1);
            }
            if (record.getAlertTriggered() == null) {
                record.setAlertTriggered(0);
            }
            if (record.getAlertLevel() == null) {
                record.setAlertLevel(1);
            }
            coneDetectionRecordMapper.insert(record);
            log.info("创建锥桶检测记录: recordNo={}, planId={}", record.getRecordNo(), record.getPlanId());
        } else {
            ConeDetectionRecord exist = getById(record.getId());
            if (exist == null) {
                throw new BusinessException("锥桶检测记录不存在");
            }
            coneDetectionRecordMapper.updateById(record);
            log.info("更新锥桶检测记录: recordId={}", record.getId());
        }

        if (record.getAlertTriggered() != null && record.getAlertTriggered() == 1
                && record.getPlanId() != null) {
            constructionPlanService.incrementEventCount(record.getPlanId(), "CONE_MISSING");
        }

        return record;
    }

    @Transactional
    public void delete(Long id) {
        ConeDetectionRecord exist = getById(id);
        if (exist == null) {
            throw new BusinessException("锥桶检测记录不存在");
        }
        coneDetectionRecordMapper.deleteById(id);
        log.info("删除锥桶检测记录: recordId={}", id);
    }

    private void calculateCompliance(ConeDetectionRecord record) {
        Integer detected = record.getDetectedConeCount() != null ? record.getDetectedConeCount() : 0;
        Integer standard = record.getStandardConeCount() != null ? record.getStandardConeCount() : 0;

        if (standard > 0) {
            int missing = Math.max(0, standard - detected);
            int extra = Math.max(0, detected - standard);
            record.setMissingConeCount(missing);
            record.setExtraConeCount(extra);

            BigDecimal complianceRate = BigDecimal.valueOf(detected)
                    .divide(BigDecimal.valueOf(standard), 2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
            if (complianceRate.compareTo(BigDecimal.valueOf(100)) > 0) {
                complianceRate = BigDecimal.valueOf(100.00);
            }
            record.setComplianceRate(complianceRate);

            boolean isCompliant = missing == 0 && extra <= standard * 0.1;
            record.setIsCompliant(isCompliant ? 1 : 0);

            if (!isCompliant && missing >= 3) {
                record.setAlertTriggered(1);
                record.setAlertLevel(missing >= 5 ? 3 : 2);
            }
        } else {
            record.setMissingConeCount(0);
            record.setExtraConeCount(detected);
            record.setComplianceRate(BigDecimal.valueOf(100.00));
            record.setIsCompliant(1);
        }
    }

    public List<ConeDetectionRecord> getRecentRecords(Long cameraId, int limit) {
        LambdaQueryWrapper<ConeDetectionRecord> wrapper = new LambdaQueryWrapper<>();
        if (cameraId != null) {
            wrapper.eq(ConeDetectionRecord::getCameraId, cameraId);
        }
        wrapper.orderByDesc(ConeDetectionRecord::getDetectionTime)
                .last("LIMIT " + limit);
        return coneDetectionRecordMapper.selectList(wrapper);
    }

    public long countNonCompliantToday(Long planId) {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LambdaQueryWrapper<ConeDetectionRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConeDetectionRecord::getPlanId, planId)
                .eq(ConeDetectionRecord::getIsCompliant, 0)
                .ge(ConeDetectionRecord::getDetectionTime, startOfDay);
        return coneDetectionRecordMapper.selectCount(wrapper);
    }
}
