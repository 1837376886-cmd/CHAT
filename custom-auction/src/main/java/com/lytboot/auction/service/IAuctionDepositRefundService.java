package com.lytboot.auction.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lytboot.auction.domain.entity.AuctionDepositRefund;

import java.math.BigDecimal;
import java.util.List;

/**
 * 保证金退款记录Service接口
 */
public interface IAuctionDepositRefundService extends IService<AuctionDepositRefund> {

    /**
     * 生成退款申请（流拍时给所有人）
     */
    void generateRefundForFlow(Long activityId);

    /**
     * 成交时给非中标人生成退款申请
     */
    void generateRefundForNonWinners(Long activityId, Long winnerUserId);

    /**
     * 生成中标人的退款申请（违约或尾款结算后）
     */
    void generateRefundForDealUser(Long activityId, Long userId);

    /**
     * 确认退款（设置实际退款金额及凭证）
     */
    void confirmRefund(Long id, BigDecimal actualRefundPrice, String refundVoucher);

    /**
     * 根据活动查询
     */
    List<AuctionDepositRefund> selectByActivityId(Long activityId);
}
