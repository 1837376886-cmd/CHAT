package com.lytboot.auction.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lytboot.auction.domain.entity.AuctionBid;

import java.util.List;

/**
 * 出价记录Service接口
 */
public interface IAuctionBidService extends IService<AuctionBid> {

    /**
     * 获取出价次数
     */
    int getBidCount(Long activityId);

    /**
     * 查询活动出价列表
     */
    List<AuctionBid> selectByActivityId(Long activityId);

    /**
     * 查询最高出价
     */
    AuctionBid selectHighestBid(Long activityId);
}
