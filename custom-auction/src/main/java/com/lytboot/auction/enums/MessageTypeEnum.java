package com.lytboot.auction.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 活动消息类型枚举
 */
@Getter
@AllArgsConstructor
public enum MessageTypeEnum {

    SYSTEM("1", "系统"),
    REGISTER("2", "报名"),
    BID("3", "出价"),
    DELAY("4", "延时"),
    DEAL("5", "成交"),
    FLOW("6", "流拍");

    private final String code;
    private final String desc;

    public static MessageTypeEnum of(String code) {
        for (MessageTypeEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}
