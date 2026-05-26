package com.lytboot.auction.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 商品操作类型枚举
 */
@Getter
@AllArgsConstructor
public enum ProductOperationTypeEnum {

    SUBMIT("submit", "提交"),
    AUDIT("audit", "审核"),
    REGISTER("register", "报名"),
    DEPOSIT("deposit", "缴纳"),
    SURVEY("survey", "踏勘"),
    BID("bid", "出价"),
    DEAL("deal", "成交"),
    REFUND("refund", "退款"),
    SETTLEMENT_AUDIT("settlement_audit", "结算审核");

    private final String code;
    private final String desc;

    public static ProductOperationTypeEnum of(String code) {
        for (ProductOperationTypeEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }
}
