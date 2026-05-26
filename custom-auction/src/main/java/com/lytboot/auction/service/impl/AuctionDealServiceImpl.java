package com.lytboot.auction.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lytboot.auction.domain.entity.AuctionActivity;
import com.lytboot.auction.domain.entity.AuctionBid;
import com.lytboot.auction.domain.entity.AuctionDeal;
import com.lytboot.auction.enums.AuctionActivityStatusEnum;
import com.lytboot.auction.enums.ProductOperationTypeEnum;
import com.lytboot.auction.enums.SettlementStatusEnum;
import com.lytboot.auction.mapper.AuctionActivityMapper;
import com.lytboot.auction.mapper.AuctionBidMapper;
import com.lytboot.auction.mapper.AuctionDealMapper;
import com.lytboot.auction.service.IAuctionDealService;
import com.lytboot.auction.service.IAuctionDepositRefundService;
import com.lytboot.auction.service.IProductOperationLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 成交记录Service实现
 */
@Service
public class AuctionDealServiceImpl extends ServiceImpl<AuctionDealMapper, AuctionDeal> implements IAuctionDealService {

    @Autowired
    private AuctionDealMapper dealMapper;

    @Autowired
    private AuctionActivityMapper activityMapper;

    @Autowired
    private AuctionBidMapper bidMapper;

    @Autowired
    private IAuctionDepositRefundService depositRefundService;

    @Autowired
    private IProductOperationLogService operationLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuctionDeal createDeal(Long activityId) {
        AuctionActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new RuntimeException("活动不存在");
        }

        AuctionBid highestBid = bidMapper.selectHighestBid(activityId);
        if (highestBid == null) {
            throw new RuntimeException("没有出价记录");
        }

        AuctionDeal deal = new AuctionDeal();
        deal.setActivityId(activityId);
        deal.setItemId(activity.getProductId());
        deal.setUserId(highestBid.getUserId());
        deal.setRealName(highestBid.getRealName());
        deal.setDealPrice(highestBid.getBidPrice());
        deal.setDealTime(LocalDateTime.now());
        deal.setSettlementStatus(SettlementStatusEnum.UNSETTLED.getCode());
        deal.setBreachStatus("0");
        dealMapper.insert(deal);

        // 更新活动状态为已成交
        AuctionActivity update = new AuctionActivity();
        update.setId(activityId);
        update.setStatus(AuctionActivityStatusEnum.DEAL.getCode());
        activityMapper.updateById(update);

        operationLogService.addLog(activity.getProductId(), ProductOperationTypeEnum.DEAL.getCode(), activityId);

        return deal;
    }

    @Override
    public AuctionDeal selectByActivityId(Long activityId) {
        return dealMapper.selectByActivityId(activityId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markBreach(Long id) {
        AuctionDeal deal = dealMapper.selectById(id);
        if (deal == null) {
            throw new RuntimeException("成交记录不存在");
        }
        dealMapper.markBreach(id);
        // 违约后给中标人生成保证金退款申请
        depositRefundService.generateRefundForDealUser(deal.getActivityId(), deal.getUserId());

        AuctionActivity activity = activityMapper.selectById(deal.getActivityId());
        if (activity != null) {
            operationLogService.addLog(activity.getProductId(), ProductOperationTypeEnum.AUDIT.getCode(), deal.getActivityId());
        }
    }
}
