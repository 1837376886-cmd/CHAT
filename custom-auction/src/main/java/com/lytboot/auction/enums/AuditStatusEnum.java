package com.lytboot.auction.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 审批状态枚举
 */
@Getter
@AllArgsConstructor
public enum AuditStatusEnum {

    PENDING("0", "待审批"),
    PASSED("1", "通过"),
    REJECTED("2", "拒绝");

    private final String code;
    private final String desc;

    public static AuditStatusEnum of(String code) {
        for (AuditStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}
