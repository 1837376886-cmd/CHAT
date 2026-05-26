package com.lytboot.auction.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lytboot.auction.domain.entity.AuctionMessage;

import java.util.List;

/**
 * 活动消息Service接口
 */
public interface IAuctionMessageService extends IService<AuctionMessage> {

    /**
     * 查询活动消息列表
     */
    List<AuctionMessage> selectByActivityId(Long activityId);

    /**
     * 发送系统消息
     */
    void sendSystemMessage(Long activityId, String content);
}
