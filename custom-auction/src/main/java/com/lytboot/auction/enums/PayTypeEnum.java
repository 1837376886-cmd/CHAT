package com.lytboot.auction.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 支付方式枚举
 */
@Getter
@AllArgsConstructor
public enum PayTypeEnum {

    ONLINE("1", "线上缴纳"),
    OFFLINE("2", "线下缴纳");

    private final String code;
    private final String desc;

    public static PayTypeEnum of(String code) {
        for (PayTypeEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}
