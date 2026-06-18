package com.traffic.alert.rule.enums;

import lombok.Getter;

@Getter
public enum GatewayType {

    EXCLUSIVE(1, "排他网关", "只有一条路径满足条件时执行，互斥"),
    PARALLEL(2, "并行网关", "所有路径同时执行，无需条件"),
    INCLUSIVE(3, "包容网关", "满足条件的所有路径都执行，多条件分支");

    private final Integer code;
    private final String name;
    private final String description;

    GatewayType(Integer code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    public static GatewayType of(Integer code) {
        if (code == null) {
            return EXCLUSIVE;
        }
        for (GatewayType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return EXCLUSIVE;
    }
}
