package com.lytboot.auction.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lytboot.auction.domain.entity.AuctionMessage;
import com.lytboot.auction.enums.MessageTypeEnum;
import com.lytboot.auction.mapper.AuctionMessageMapper;
import com.lytboot.auction.service.IAuctionMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 活动消息Service实现
 */
@Service
public class AuctionMessageServiceImpl extends ServiceImpl<AuctionMessageMapper, AuctionMessage> implements IAuctionMessageService {

    @Autowired
    private AuctionMessageMapper messageMapper;

    @Override
    public List<AuctionMessage> selectByActivityId(Long activityId) {
        return messageMapper.selectByActivityId(activityId);
    }

    @Override
    public void sendSystemMessage(Long activityId, String content) {
        AuctionMessage message = new AuctionMessage();
        message.setActivityId(activityId);
        message.setContent(content);
        messageMapper.insert(message);
    }
}
