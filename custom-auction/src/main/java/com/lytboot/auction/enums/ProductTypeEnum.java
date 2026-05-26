package com.lytboot.auction.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 商品类型枚举
 */
@Getter
@AllArgsConstructor
public enum ProductTypeEnum {

    GOODS("1", "货品类"),
    RESOURCE("2", "资源包"),
    SECONDHAND("3", "二手设备");

    private final String code;
    private final String desc;

    public static ProductTypeEnum of(String code) {
        for (ProductTypeEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}
