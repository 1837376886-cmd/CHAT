package com.lytboot.auction.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lytboot.auction.domain.entity.AuctionDeposit;
import com.lytboot.auction.mapper.AuctionDepositMapper;
import com.lytboot.auction.service.IAuctionDepositService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 保证金记录Service实现
 */
@Service
public class AuctionDepositServiceImpl extends ServiceImpl<AuctionDepositMapper, AuctionDeposit> implements IAuctionDepositService {

    @Autowired
    private AuctionDepositMapper depositMapper;

    @Override
    public AuctionDeposit selectByActivityAndUser(Long activityId, Long userId) {
        return depositMapper.selectByActivityAndUser(activityId, userId);
    }

    @Override
    public List<AuctionDeposit> selectPaidByActivity(Long activityId) {
        return depositMapper.selectPaidByActivity(activityId);
    }
}
