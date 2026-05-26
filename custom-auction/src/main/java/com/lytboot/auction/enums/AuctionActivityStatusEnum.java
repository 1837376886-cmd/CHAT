package com.lytboot.auction.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 拍卖活动状态枚举
 */
@Getter
@AllArgsConstructor
public enum AuctionActivityStatusEnum {

    PENDING_PUBLISH("0", "待发布"),
    PENDING_START("1", "待启动"),
    REGISTERING("2", "报名中"),
    PENDING_BID("3", "待拍中"),
    BIDDING("4", "竞拍中"),
    ENDED("5", "已结束"),
    DEAL("6", "已成交"),
    FLOW("7", "流拍"),
    CANCELLED("8", "已取消");

    private final String code;
    private final String desc;

    public static AuctionActivityStatusEnum of(String code) {
        for (AuctionActivityStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}
