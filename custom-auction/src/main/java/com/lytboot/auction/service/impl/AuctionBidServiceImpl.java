package com.lytboot.auction.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lytboot.auction.domain.entity.AuctionBid;
import com.lytboot.auction.mapper.AuctionBidMapper;
import com.lytboot.auction.service.IAuctionBidService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 出价记录Service实现
 */
@Service
public class AuctionBidServiceImpl extends ServiceImpl<AuctionBidMapper, AuctionBid> implements IAuctionBidService {

    @Autowired
    private AuctionBidMapper bidMapper;

    @Override
    public int getBidCount(Long activityId) {
        return bidMapper.getBidCount(activityId);
    }

    @Override
    public List<AuctionBid> selectByActivityId(Long activityId) {
        return bidMapper.selectByActivityId(activityId);
    }

    @Override
    public AuctionBid selectHighestBid(Long activityId) {
        return bidMapper.selectHighestBid(activityId);
    }
}
