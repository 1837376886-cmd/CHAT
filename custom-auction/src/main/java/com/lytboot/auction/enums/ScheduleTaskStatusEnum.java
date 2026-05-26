package com.lytboot.auction.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 调度任务状态枚举
 */
@Getter
@AllArgsConstructor
public enum ScheduleTaskStatusEnum {

    PENDING("0", "待执行"),
    EXECUTED("1", "已执行"),
    CANCELLED("2", "已取消");

    private final String code;
    private final String desc;
}
