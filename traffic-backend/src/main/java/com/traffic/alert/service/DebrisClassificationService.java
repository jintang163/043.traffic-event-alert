package com.traffic.alert.service;

import com.traffic.alert.enums.DebrisCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class DebrisClassificationService {

    private static final List<String> TIRE_KEYWORDS = Arrays.asList(
            "tire", "tyre", "轮胎", "车胎", "轮毂", "备胎"
    );
    private static final List<String> CARGO_KEYWORDS = Arrays.asList(
            "cargo", "goods", "货物", "大件", "货箱", "木箱", "集装箱"
    );
    private static final List<String> CARDBOARD_KEYWORDS = Arrays.asList(
            "cardboard", "box", "纸箱", "纸盒", "纸壳", "包装箱"
    );
    private static final List<String> ANIMAL_KEYWORDS = Arrays.asList(
            "animal", "dog", "cat", "cow", "sheep", "pig", "horse",
            "动物", "狗", "猫", "牛", "羊", "猪", "马", "家禽", "牲畜", "野物"
    );
    private static final List<String> DEBRIS_BAG_KEYWORDS = Arrays.asList(
            "bag", "sack", "package", "包裹", "布袋", "编织袋", "麻袋", "垃圾袋"
    );
    private static final List<String> CONSTRUCTION_KEYWORDS = Arrays.asList(
            "construction", "brick", "stone", "sand", "cement",
            "建筑", "砖头", "砖块", "石块", "沙子", "水泥", "建材"
    );
    private static final List<String> METAL_KEYWORDS = Arrays.asList(
            "metal", "iron", "steel", "screw", "bolt", "nail",
            "金属", "铁", "钢", "螺丝", "螺栓", "钉子", "钢筋", "角铁"
    );
    private static final List<String> PLASTIC_KEYWORDS = Arrays.asList(
            "plastic", "bottle", "桶", "塑料", "塑料瓶", "塑料桶", "塑料件"
    );
    private static final List<String> PAPER_KEYWORDS = Arrays.asList(
            "paper", "纸片", "纸张", "报纸", "传单"
    );
    private static final List<String> GLASS_KEYWORDS = Arrays.asList(
            "glass", "玻璃", "玻璃片", "玻璃渣"
    );

    public DebrisCategory classify(String aiClassName, String description, Map<String, Object> extraContext) {
        String concat = ((aiClassName == null ? "" : aiClassName) + " "
                + (description == null ? "" : description)).toLowerCase(Locale.ROOT);

        if (matchesAny(concat, TIRE_KEYWORDS)) return DebrisCategory.TIRE;
        if (matchesAny(concat, CARGO_KEYWORDS)) return DebrisCategory.CARGO;
        if (matchesAny(concat, CARDBOARD_KEYWORDS)) return DebrisCategory.CARDBOARD;
        if (matchesAny(concat, ANIMAL_KEYWORDS)) return DebrisCategory.ANIMAL;
        if (matchesAny(concat, DEBRIS_BAG_KEYWORDS)) return DebrisCategory.DEBRIS_BAG;
        if (matchesAny(concat, CONSTRUCTION_KEYWORDS)) return DebrisCategory.CONSTRUCTION;
        if (matchesAny(concat, METAL_KEYWORDS)) return DebrisCategory.METAL;
        if (matchesAny(concat, PLASTIC_KEYWORDS)) return DebrisCategory.PLASTIC;
        if (matchesAny(concat, PAPER_KEYWORDS)) return DebrisCategory.PAPER;
        if (matchesAny(concat, GLASS_KEYWORDS)) return DebrisCategory.GLASS;

        if (extraContext != null) {
            Object cls = extraContext.get("className");
            if (cls != null) {
                String extraCls = cls.toString().toLowerCase(Locale.ROOT);
                if (matchesAny(extraCls, TIRE_KEYWORDS)) return DebrisCategory.TIRE;
                if (matchesAny(extraCls, ANIMAL_KEYWORDS)) return DebrisCategory.ANIMAL;
            }
        }

        return DebrisCategory.OTHER;
    }

    public DebrisCategory classifyFromAi(String aiClassName) {
        if (!StringUtils.hasText(aiClassName)) {
            return DebrisCategory.OTHER;
        }
        return classify(aiClassName, null, null);
    }

    public int resolveAlertLevel(DebrisCategory category) {
        return category != null ? category.getDefaultLevel() : 1;
    }

    public int resolveAlertLevel(String debrisCategoryCode) {
        return DebrisCategory.resolveLevel(debrisCategoryCode);
    }

    private boolean matchesAny(String text, List<String> keywords) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (String kw : keywords) {
            if (text.contains(kw.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
