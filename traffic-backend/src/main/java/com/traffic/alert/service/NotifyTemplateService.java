package com.traffic.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.alert.common.PageResult;
import com.traffic.alert.dto.NotifyTemplateQuery;
import com.traffic.alert.entity.AlertEvent;
import com.traffic.alert.entity.NotifyTemplate;
import com.traffic.alert.enums.DebrisCategory;
import com.traffic.alert.mapper.NotifyTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyTemplateService {

    private final NotifyTemplateMapper notifyTemplateMapper;
    private static final DecimalFormat CONFIDENCE_FMT = new DecimalFormat("0.##");

    public NotifyTemplate getById(Long id) {
        return notifyTemplateMapper.selectById(id);
    }

    public List<NotifyTemplate> listAll() {
        return notifyTemplateMapper.selectList(new LambdaQueryWrapper<NotifyTemplate>()
                .eq(NotifyTemplate::getStatus, 1)
                .orderByAsc(NotifyTemplate::getId));
    }

    public PageResult<NotifyTemplate> page(NotifyTemplateQuery query) {
        Page<NotifyTemplate> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<NotifyTemplate> wrapper = new LambdaQueryWrapper<>();
        if (query.getChannelType() != null && !query.getChannelType().isEmpty()) {
            wrapper.eq(NotifyTemplate::getChannelType, query.getChannelType());
        }
        if (query.getEventType() != null && !query.getEventType().isEmpty()) {
            wrapper.eq(NotifyTemplate::getEventType, query.getEventType());
        }
        if (query.getEventLevel() != null) {
            wrapper.eq(NotifyTemplate::getEventLevel, query.getEventLevel());
        }
        if (query.getStatus() != null) {
            wrapper.eq(NotifyTemplate::getStatus, query.getStatus());
        }
        notifyTemplateMapper.selectPage(page, wrapper);
        return PageResult.of(page.getTotal(), page.getRecords(), page.getCurrent(), query.getSize());
    }

    public NotifyTemplate save(NotifyTemplate template) {
        if (template.getId() == null) {
            notifyTemplateMapper.insert(template);
        } else {
            notifyTemplateMapper.updateById(template);
        }
        return template;
    }

    public void delete(Long id) {
        notifyTemplateMapper.deleteById(id);
    }

    public NotifyTemplate findBestTemplate(String channelType, String eventType, Integer eventLevel) {
        LambdaQueryWrapper<NotifyTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NotifyTemplate::getChannelType, channelType)
                .eq(NotifyTemplate::getStatus, 1);
        wrapper.and(w -> {
            w.eq(NotifyTemplate::getEventType, eventType)
                    .eq(NotifyTemplate::getEventLevel, eventLevel);
            w.or(ww -> ww.eq(NotifyTemplate::getEventType, eventType).isNull(NotifyTemplate::getEventLevel));
            w.or(ww -> ww.isNull(NotifyTemplate::getEventType).eq(NotifyTemplate::getEventLevel, eventLevel));
            w.or(ww -> ww.isNull(NotifyTemplate::getEventType).isNull(NotifyTemplate::getEventLevel));
        });
        wrapper.orderByDesc(NotifyTemplate::getEventType).orderByDesc(NotifyTemplate::getEventLevel)
                .last("LIMIT 1");
        return notifyTemplateMapper.selectOne(wrapper);
    }

    public String renderTemplate(String template, AlertEvent event) {
        return renderTemplate(template, event, null);
    }

    public String renderTemplate(String template, AlertEvent event, Map<String, String> extraVars) {
        if (template == null || template.isEmpty()) return "";
        Map<String, String> vars = buildTemplateVars(event);
        if (extraVars != null) {
            vars.putAll(extraVars);
        }
        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue() != null ? entry.getValue() : "");
        }
        return result;
    }

    private Map<String, String> buildTemplateVars(AlertEvent event) {
        Map<String, String> vars = new HashMap<>();
        vars.put("eventType", event.getEventType() != null ? event.getEventType() : "");
        vars.put("eventTypeText", buildEventTypeText(event));
        vars.put("eventLevel", event.getEventLevel() != null ? String.valueOf(event.getEventLevel()) : "1");
        vars.put("levelText", switch (event.getEventLevel() != null ? event.getEventLevel() : 1) {
            case 3 -> "【紧急】";
            case 2 -> "【严重】";
            default -> "【一般】";
        });
        vars.put("levelTextShort", switch (event.getEventLevel() != null ? event.getEventLevel() : 1) {
            case 3 -> "紧急";
            case 2 -> "严重";
            default -> "一般";
        });
        vars.put("cameraName", event.getCameraName() != null ? event.getCameraName() : "");
        vars.put("location", event.getLocation() != null ? event.getLocation() : "");
        vars.put("eventTime", event.getEventTime() != null ? event.getEventTime().toString() : "");
        vars.put("description", event.getDescription() != null ? event.getDescription() : "");
        BigDecimal conf = event.getConfidence() != null
                ? event.getConfidence().multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;
        vars.put("confidence", CONFIDENCE_FMT.format(conf));
        vars.put("confidenceRaw", event.getConfidence() != null ? event.getConfidence().toPlainString() : "0");
        vars.put("eventNo", event.getEventNo() != null ? event.getEventNo() : "");
        vars.put("longitude", event.getLongitude() != null ? event.getLongitude().toPlainString() : "");
        vars.put("latitude", event.getLatitude() != null ? event.getLatitude().toPlainString() : "");
        vars.put("debrisCategory", event.getDebrisCategory() != null ? event.getDebrisCategory() : "");
        vars.put("debrisCategoryText", buildDebrisCategoryText(event));
        vars.put("accidentSeverity", event.getAccidentSeverity() != null ? event.getAccidentSeverity() : "");
        vars.put("accidentSeverityText", event.getAccidentSeverityLabel() != null ? event.getAccidentSeverityLabel() : "");
        vars.put("accidentVehicles", event.getAccidentVehicles() != null ? String.valueOf(event.getAccidentVehicles()) : "");
        vars.put("accidentCasualty", event.getAccidentCasualty() != null ? String.valueOf(event.getAccidentCasualty()) : "");
        return vars;
    }

    private String buildEventTypeText(AlertEvent event) {
        if (event.getEventType() == null) return "";
        return switch (event.getEventType()) {
            case "ACCIDENT" -> "交通事故";
            case "REVERSE" -> "车辆逆行";
            case "DEBRIS" -> {
                if (event.getDebrisCategory() != null && !event.getDebrisCategory().isEmpty()) {
                    try {
                        yield "抛洒物-" + DebrisCategory.of(event.getDebrisCategory()).getLabel();
                    } catch (Exception ignored) {}
                }
                yield "路面抛洒物";
            }
            default -> event.getEventType();
        };
    }

    private String buildDebrisCategoryText(AlertEvent event) {
        if (event.getDebrisCategory() == null || event.getDebrisCategory().isEmpty()) return "";
        try {
            return DebrisCategory.of(event.getDebrisCategory()).getLabel();
        } catch (Exception e) {
            return event.getDebrisCategory();
        }
    }
}
