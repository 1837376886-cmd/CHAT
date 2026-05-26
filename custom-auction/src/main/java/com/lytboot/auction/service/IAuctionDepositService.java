package com.lytboot.auction.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lytboot.auction.domain.entity.AuctionDeposit;

import java.util.List;

/**
 * 保证金记录Service接口
 */
public interface IAuctionDepositService extends IService<AuctionDeposit> {

    /**
     * 根据活动和用户查询
     */
    AuctionDeposit selectByActivityAndUser(Long activityId, Long userId);

    /**
     * 查询活动已缴纳的保证金列表
     */
    List<AuctionDeposit> selectPaidByActivity(Long activityId);
}
