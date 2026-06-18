package com.traffic.alert.service;

import com.traffic.alert.enums.DebrisCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class DebrisClassificationService {

    private static final Map<String, DebrisCategory> DIRECT_MODEL_MAP = new HashMap<>();

    private static final Map<DebrisCategory, List<Pattern>> WORD_BOUNDARY_PATTERNS = new EnumMap<>(DebrisCategory.class);

    static {
        DIRECT_MODEL_MAP.put("tire", DebrisCategory.TIRE);
        DIRECT_MODEL_MAP.put("tyre", DebrisCategory.TIRE);
        DIRECT_MODEL_MAP.put("wheel", DebrisCategory.TIRE);
        DIRECT_MODEL_MAP.put("spare_tire", DebrisCategory.TIRE);
        DIRECT_MODEL_MAP.put("tire_roll", DebrisCategory.TIRE);
        DIRECT_MODEL_MAP.put("cargo", DebrisCategory.CARGO);
        DIRECT_MODEL_MAP.put("goods", DebrisCategory.CARGO);
        DIRECT_MODEL_MAP.put("freight", DebrisCategory.CARGO);
        DIRECT_MODEL_MAP.put("container", DebrisCategory.CARGO);
        DIRECT_MODEL_MAP.put("wooden_case", DebrisCategory.CARGO);
        DIRECT_MODEL_MAP.put("cardboard", DebrisCategory.CARDBOARD);
        DIRECT_MODEL_MAP.put("carton", DebrisCategory.CARDBOARD);
        DIRECT_MODEL_MAP.put("box", DebrisCategory.CARDBOARD);
        DIRECT_MODEL_MAP.put("paper_box", DebrisCategory.CARDBOARD);
        DIRECT_MODEL_MAP.put("animal", DebrisCategory.ANIMAL);
        DIRECT_MODEL_MAP.put("dog", DebrisCategory.ANIMAL);
        DIRECT_MODEL_MAP.put("cat", DebrisCategory.ANIMAL);
        DIRECT_MODEL_MAP.put("cow", DebrisCategory.ANIMAL);
        DIRECT_MODEL_MAP.put("sheep", DebrisCategory.ANIMAL);
        DIRECT_MODEL_MAP.put("pig", DebrisCategory.ANIMAL);
        DIRECT_MODEL_MAP.put("horse", DebrisCategory.ANIMAL);
        DIRECT_MODEL_MAP.put("deer", DebrisCategory.ANIMAL);
        DIRECT_MODEL_MAP.put("livestock", DebrisCategory.ANIMAL);
        DIRECT_MODEL_MAP.put("bag", DebrisCategory.DEBRIS_BAG);
        DIRECT_MODEL_MAP.put("sack", DebrisCategory.DEBRIS_BAG);
        DIRECT_MODEL_MAP.put("package", DebrisCategory.DEBRIS_BAG);
        DIRECT_MODEL_MAP.put("woven_bag", DebrisCategory.DEBRIS_BAG);
        DIRECT_MODEL_MAP.put("trash_bag", DebrisCategory.DEBRIS_BAG);
        DIRECT_MODEL_MAP.put("parcel", DebrisCategory.DEBRIS_BAG);
        DIRECT_MODEL_MAP.put("brick", DebrisCategory.CONSTRUCTION);
        DIRECT_MODEL_MAP.put("stone", DebrisCategory.CONSTRUCTION);
        DIRECT_MODEL_MAP.put("rock", DebrisCategory.CONSTRUCTION);
        DIRECT_MODEL_MAP.put("sand", DebrisCategory.CONSTRUCTION);
        DIRECT_MODEL_MAP.put("cement", DebrisCategory.CONSTRUCTION);
        DIRECT_MODEL_MAP.put("construction", DebrisCategory.CONSTRUCTION);
        DIRECT_MODEL_MAP.put("metal", DebrisCategory.METAL);
        DIRECT_MODEL_MAP.put("iron", DebrisCategory.METAL);
        DIRECT_MODEL_MAP.put("steel", DebrisCategory.METAL);
        DIRECT_MODEL_MAP.put("screw", DebrisCategory.METAL);
        DIRECT_MODEL_MAP.put("bolt", DebrisCategory.METAL);
        DIRECT_MODEL_MAP.put("nail", DebrisCategory.METAL);
        DIRECT_MODEL_MAP.put("rebar", DebrisCategory.METAL);
        DIRECT_MODEL_MAP.put("plastic", DebrisCategory.PLASTIC);
        DIRECT_MODEL_MAP.put("plastic_bottle", DebrisCategory.PLASTIC);
        DIRECT_MODEL_MAP.put("bottle", DebrisCategory.PLASTIC);
        DIRECT_MODEL_MAP.put("bucket", DebrisCategory.PLASTIC);
        DIRECT_MODEL_MAP.put("barrel", DebrisCategory.PLASTIC);
        DIRECT_MODEL_MAP.put("paper", DebrisCategory.PAPER);
        DIRECT_MODEL_MAP.put("tissue", DebrisCategory.PAPER);
        DIRECT_MODEL_MAP.put("newspaper", DebrisCategory.PAPER);
        DIRECT_MODEL_MAP.put("flyer", DebrisCategory.PAPER);
        DIRECT_MODEL_MAP.put("glass", DebrisCategory.GLASS);
        DIRECT_MODEL_MAP.put("glass_shard", DebrisCategory.GLASS);
        DIRECT_MODEL_MAP.put("debris", DebrisCategory.OTHER);
        DIRECT_MODEL_MAP.put("unknown", DebrisCategory.OTHER);
        DIRECT_MODEL_MAP.put("other", DebrisCategory.OTHER);

        registerPattern(DebrisCategory.TIRE,
                "轮胎", "车胎", "轮毂", "备胎", "tire", "tyre", "wheel");
        registerPattern(DebrisCategory.CARGO,
                "货物", "货箱", "木箱", "集装箱", "大件", "cargo", "goods", "freight", "container");
        registerPattern(DebrisCategory.CARDBOARD,
                "纸箱", "纸盒", "纸壳", "包装箱", "cardboard", "carton", "box");
        registerPattern(DebrisCategory.ANIMAL,
                "动物", "狗", "猫", "牛", "羊", "猪", "马", "家禽", "牲畜", "野物",
                "animal", "dog", "cat", "cow", "sheep", "pig", "horse", "deer", "livestock");
        registerPattern(DebrisCategory.DEBRIS_BAG,
                "包裹", "布袋", "编织袋", "麻袋", "垃圾袋", "蛇皮袋",
                "bag", "sack", "package", "parcel");
        registerPattern(DebrisCategory.CONSTRUCTION,
                "砖头", "砖块", "石块", "沙子", "水泥", "建材", "建筑",
                "brick", "stone", "rock", "sand", "cement", "construction");
        registerPattern(DebrisCategory.METAL,
                "金属", "铁", "钢", "螺丝", "螺栓", "钉子", "钢筋", "角铁",
                "metal", "iron", "steel", "screw", "bolt", "nail", "rebar");
        registerPattern(DebrisCategory.PLASTIC,
                "塑料", "塑料瓶", "塑料桶", "塑料件", "桶",
                "plastic", "bottle", "bucket", "barrel");
        registerPattern(DebrisCategory.PAPER,
                "纸片", "纸张", "报纸", "传单", "paper", "tissue", "newspaper", "flyer");
        registerPattern(DebrisCategory.GLASS,
                "玻璃", "玻璃片", "玻璃渣", "glass");
    }

    private static void registerPattern(DebrisCategory category, String... keywords) {
        List<Pattern> patterns = new ArrayList<>();
        for (String kw : keywords) {
            patterns.add(Pattern.compile("(?<![A-Za-z0-9_\u4e00-\u9fa5])"
                    + Pattern.quote(kw)
                    + "(?![A-Za-z0-9_\u4e00-\u9fa5])", Pattern.CASE_INSENSITIVE));
        }
        WORD_BOUNDARY_PATTERNS.put(category, patterns);
    }

    public boolean isValidCategoryCode(String code) {
        if (code == null || code.isEmpty()) {
            return false;
        }
        for (DebrisCategory c : DebrisCategory.values()) {
            if (c.getCode().equalsIgnoreCase(code)) {
                return true;
            }
        }
        return false;
    }

    public DebrisCategory validateAndResolve(String debrisCategoryCode) {
        if (!StringUtils.hasText(debrisCategoryCode)) {
            return null;
        }
        for (DebrisCategory c : DebrisCategory.values()) {
            if (c.getCode().equalsIgnoreCase(debrisCategoryCode)) {
                return c;
            }
        }
        log.warn("AI回调传入不合法debrisCategory：'{}'，将触发自动分类", debrisCategoryCode);
        return null;
    }

    public DebrisCategory classify(String aiClassName, String description, Map<String, Object> extraContext) {
        if (StringUtils.hasText(aiClassName)) {
            DebrisCategory direct = DIRECT_MODEL_MAP.get(aiClassName.trim().toLowerCase(Locale.ROOT));
            if (direct != null) {
                log.debug("模型类别直接映射: className={} -> {}", aiClassName, direct.getCode());
                return direct;
            }
        }

        if (extraContext != null) {
            Object cls = extraContext.get("className");
            if (cls != null) {
                DebrisCategory direct = DIRECT_MODEL_MAP.get(cls.toString().trim().toLowerCase(Locale.ROOT));
                if (direct != null) {
                    return direct;
                }
            }
        }

        String concat = ((aiClassName == null ? "" : aiClassName) + " "
                + (description == null ? "" : description) + " "
                + (extraContext != null && extraContext.get("description") != null
                ? extraContext.get("description").toString() : "")).toLowerCase(Locale.ROOT);

        for (Map.Entry<DebrisCategory, List<Pattern>> entry : WORD_BOUNDARY_PATTERNS.entrySet()) {
            for (Pattern p : entry.getValue()) {
                Matcher m = p.matcher(concat);
                if (m.find()) {
                    log.debug("词边界匹配命中: category={}, pattern={}, text='{}'",
                            entry.getKey().getCode(), p.pattern(), m.group());
                    return entry.getKey();
                }
            }
        }

        return DebrisCategory.OTHER;
    }

    public DebrisCategory classifyFromAi(String aiClassName) {
        if (!StringUtils.hasText(aiClassName)) {
            return DebrisCategory.OTHER;
        }
        DebrisCategory direct = DIRECT_MODEL_MAP.get(aiClassName.trim().toLowerCase(Locale.ROOT));
        if (direct != null) {
            return direct;
        }
        return classify(aiClassName, null, null);
    }

    public int resolveAlertLevel(DebrisCategory category) {
        return category != null ? category.getDefaultLevel() : 1;
    }

    public int resolveAlertLevel(String debrisCategoryCode) {
        return DebrisCategory.resolveLevel(debrisCategoryCode);
    }
}
