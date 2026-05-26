package com.lytboot.auction.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 状态流转类型枚举
 */
@Getter
@AllArgsConstructor
public enum StateTransitionTypeEnum {

    /** publish_start_time 触发：status 1→2 */
    PENDING_TO_REGISTERING("1", "待启动→报名中", "待启动(1)→报名中(2)"),
    /** register_end_time 触发：status 2→3 */
    REGISTERING_TO_PENDING_BID("2", "报名中→结束报名", "报名中(2)→结束报名/待竞拍(3)"),
    /** auction_start_time 触发：status 3→4 */
    PENDING_BID_TO_BIDDING("3", "结束报名→竞拍中", "结束报名(3)→竞拍中(4)"),
    /** auction_end_time 触发：status 4→5/6/7 */
    BIDDING_TO_ENDED("4", "竞拍中→已结束", "竞拍中(4)→已结束(5)，再判断成交(6)/流拍(7)");

    private final String code;
    private final String desc;
    private final String transition;

    public static StateTransitionTypeEnum of(String code) {
        for (StateTransitionTypeEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}
