package com.lytboot.auction.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 应用状态枚举
 */
@Getter
@AllArgsConstructor
public enum ApplyStatusEnum {

    AUCTION("1", "拍卖"),
    MALL("2", "商城");

    private final String code;
    private final String desc;

    public static ApplyStatusEnum of(String code) {
        for (ApplyStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}
