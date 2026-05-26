package com.lytboot.auction.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 退款状态枚举
 */
@Getter
@AllArgsConstructor
public enum RefundStatusEnum {

    PENDING("0", "待退款"),
    REFUNDED("1", "已退款"),
    REJECTED("2", "拒绝");

    private final String code;
    private final String desc;

    public static RefundStatusEnum of(String code) {
        for (RefundStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}
