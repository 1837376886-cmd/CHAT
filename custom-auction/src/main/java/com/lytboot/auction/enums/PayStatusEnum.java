package com.lytboot.auction.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 支付/缴纳状态枚举
 */
@Getter
@AllArgsConstructor
public enum PayStatusEnum {

    UNPAID("0", "未缴纳"),
    PAID("1", "已缴纳"),
    REFUNDED("2", "已退还"),
    PENDING_REFUND("3", "待退款");

    private final String code;
    private final String desc;

    public static PayStatusEnum of(String code) {
        for (PayStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}
