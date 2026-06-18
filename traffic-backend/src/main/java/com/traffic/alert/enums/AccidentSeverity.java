package com.traffic.alert.enums;

import lombok.Getter;

@Getter
public enum AccidentSeverity {

    SLIGHT("SLIGHT", "轻微事故", 1, "#52c41a"),
    GENERAL("GENERAL", "一般事故", 2, "#faad14"),
    MAJOR("MAJOR", "重大事故", 3, "#ff4d4f");

    private final String code;
    private final String label;
    private final int priority;
    private final String color;

    AccidentSeverity(String code, String label, int priority, String color) {
        this.code = code;
        this.label = label;
        this.priority = priority;
        this.color = color;
    }

    public static AccidentSeverity of(String code) {
        if (code == null) return SLIGHT;
        for (AccidentSeverity s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        return SLIGHT;
    }

    public static AccidentSeverity fromPriority(int priority) {
        for (AccidentSeverity s : values()) {
            if (s.priority == priority) return s;
        }
        return SLIGHT;
    }
}
