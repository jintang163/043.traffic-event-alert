package com.traffic.alert.enums;

import lombok.Getter;

@Getter
public enum DebrisCategory {

    TIRE("TIRE", "轮胎掉落", 3),
    CARGO("CARGO", "货物掉落", 3),
    CARDBOARD("CARDBOARD", "纸箱", 2),
    ANIMAL("ANIMAL", "动物闯入", 3),
    DEBRIS_BAG("DEBRIS_BAG", "杂物袋/包裹", 2),
    CONSTRUCTION("CONSTRUCTION", "建筑材料", 2),
    METAL("METAL", "金属部件", 3),
    PLASTIC("PLASTIC", "塑料杂物", 1),
    PAPER("PAPER", "纸张/纸片", 1),
    GLASS("GLASS", "玻璃碎片", 2),
    OTHER("OTHER", "其他杂物", 1);

    private final String code;
    private final String label;
    private final int defaultLevel;

    DebrisCategory(String code, String label, int defaultLevel) {
        this.code = code;
        this.label = label;
        this.defaultLevel = defaultLevel;
    }

    public static DebrisCategory of(String code) {
        if (code == null || code.isEmpty()) {
            return OTHER;
        }
        for (DebrisCategory c : values()) {
            if (c.code.equalsIgnoreCase(code)) {
                return c;
            }
        }
        return OTHER;
    }

    public static int resolveLevel(String code) {
        return of(code).getDefaultLevel();
    }
}
