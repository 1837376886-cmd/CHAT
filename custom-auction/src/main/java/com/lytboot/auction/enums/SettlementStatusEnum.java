package com.lytboot.auction.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 尾款结算状态枚举
 */
@Getter
@AllArgsConstructor
public enum SettlementStatusEnum {

    UNSETTLED("0", "未结算"),
    SETTLED("1", "已结算"),
    BREACH("2", "违约");

    private final String code;
    private final String desc;

    public static SettlementStatusEnum of(String code) {
        for (SettlementStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}
