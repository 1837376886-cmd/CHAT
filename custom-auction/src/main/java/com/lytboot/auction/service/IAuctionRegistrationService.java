package com.lytboot.auction.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lytboot.auction.domain.entity.AuctionRegistration;

import java.util.List;

/**
 * 报名记录Service接口
 */
public interface IAuctionRegistrationService extends IService<AuctionRegistration> {

    /**
     * 用户报名
     */
    void register(Long activityId, Long userId, AuctionRegistration registration);

    /**
     * 审核报名
     */
    void auditRegistration(Long id, String auditStatus, String auditRemark, Long auditBy);

    /**
     * 缴纳保证金申请
     */
    void applyDeposit(Long registrationId, String payType, String payVoucher);

    /**
     * 审核保证金（确认保证金记录有效并同步出价资格）
     */
    void auditDeposit(Long id, String auditStatus, String auditRemark, Long auditBy);

    /**
     * 根据活动和用户查询
     */
    AuctionRegistration selectByActivityAndUser(Long activityId, Long userId);

    /**
     * 查询活动的报名列表
     */
    List<AuctionRegistration> selectByActivityId(Long activityId);
}
