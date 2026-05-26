package com.lytboot.auction.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 踏勘状态枚举
 */
@Getter
@AllArgsConstructor
public enum SurveyStatusEnum {

    UNCONFIRMED("0", "未确认"),
    CONFIRMED("1", "已确认");

    private final String code;
    private final String desc;

    public static SurveyStatusEnum of(String code) {
        for (SurveyStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}
