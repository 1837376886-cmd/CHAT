package com.lytboot.auction.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lytboot.auction.domain.entity.AuctionDeal;

/**
 * 成交记录Service接口
 */
public interface IAuctionDealService extends IService<AuctionDeal> {

    /**
     * 创建成交记录
     */
    AuctionDeal createDeal(Long activityId);

    /**
     * 根据活动查询
     */
    AuctionDeal selectByActivityId(Long activityId);

    /**
     * 标记违约
     */
    void markBreach(Long id);
}
