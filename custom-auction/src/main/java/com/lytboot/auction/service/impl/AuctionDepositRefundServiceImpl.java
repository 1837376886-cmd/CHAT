package com.lytboot.auction.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lytboot.auction.domain.entity.AuctionActivity;
import com.lytboot.auction.domain.entity.AuctionDeposit;
import com.lytboot.auction.domain.entity.AuctionDepositRefund;
import com.lytboot.auction.mapper.AuctionActivityMapper;
import com.lytboot.auction.mapper.AuctionDepositMapper;
import com.lytboot.auction.enums.ProductOperationTypeEnum;
import com.lytboot.auction.mapper.AuctionDepositRefundMapper;
import com.lytboot.auction.service.IAuctionDepositRefundService;
import com.lytboot.auction.service.IProductOperationLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 保证金退款记录Service实现
 */
@Service
public class AuctionDepositRefundServiceImpl extends ServiceImpl<AuctionDepositRefundMapper, AuctionDepositRefund> implements IAuctionDepositRefundService {

    @Autowired
    private AuctionDepositRefundMapper refundMapper;

    @Autowired
    private AuctionDepositMapper depositMapper;

    @Autowired
    private AuctionActivityMapper activityMapper;

    @Autowired
    private IProductOperationLogService operationLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generateRefundForFlow(Long activityId) {
        List<AuctionDeposit> deposits = depositMapper.selectPaidByActivity(activityId);
        for (AuctionDeposit deposit : deposits) {
            AuctionDepositRefund refund = new AuctionDepositRefund();
            refund.setActivityId(activityId);
            refund.setUserId(deposit.getUserId());
            refund.setDepositAmount(deposit.getDepositAmount());
            refund.setRefundPrice(deposit.getDepositAmount());
            refund.setActualRefundPrice(deposit.getDepositAmount());
            refund.setPayType(deposit.getPayType());
            refundMapper.insert(refund);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generateRefundForNonWinners(Long activityId, Long winnerUserId) {
        List<AuctionDeposit> deposits = depositMapper.selectPaidByActivity(activityId);
        for (AuctionDeposit deposit : deposits) {
            if (deposit.getUserId().equals(winnerUserId)) {
                continue;
            }
            AuctionDepositRefund refund = new AuctionDepositRefund();
            refund.setActivityId(activityId);
            refund.setUserId(deposit.getUserId());
            refund.setDepositAmount(deposit.getDepositAmount());
            refund.setRefundPrice(deposit.getDepositAmount());
            refund.setActualRefundPrice(deposit.getDepositAmount());
            refund.setPayType(deposit.getPayType());
            refundMapper.insert(refund);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generateRefundForDealUser(Long activityId, Long userId) {
        AuctionDeposit deposit = depositMapper.selectByActivityAndUser(activityId, userId);
        if (deposit == null) {
            return;
        }

        AuctionActivity activity = activityMapper.selectById(activityId);
        BigDecimal refundPrice = deposit.getDepositAmount();
        // 如果有佣金，扣除佣金
        if (activity != null && activity.getCommission() != null && !activity.getCommission().isEmpty()) {
            try {
                BigDecimal commissionRate = new BigDecimal(activity.getCommission()).divide(new BigDecimal("100"));
                BigDecimal commissionAmount = refundPrice.multiply(commissionRate);
                refundPrice = refundPrice.subtract(commissionAmount);
            } catch (Exception ignored) {
            }
        }

        AuctionDepositRefund refund = new AuctionDepositRefund();
        refund.setActivityId(activityId);
        refund.setUserId(userId);
        refund.setDepositAmount(deposit.getDepositAmount());
        refund.setRefundPrice(deposit.getDepositAmount());
        refund.setActualRefundPrice(refundPrice);
        refund.setPayType(deposit.getPayType());
        refundMapper.insert(refund);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmRefund(Long id, BigDecimal actualRefundPrice, String refundVoucher) {
        AuctionDepositRefund refund = new AuctionDepositRefund();
        refund.setId(id);
        refund.setActualRefundPrice(actualRefundPrice);
        refund.setRefundVoucher(refundVoucher);
        refundMapper.updateById(refund);

        AuctionDepositRefund exist = refundMapper.selectById(id);
        if (exist != null) {
            AuctionActivity activity = activityMapper.selectById(exist.getActivityId());
            if (activity != null) {
                operationLogService.addLog(activity.getProductId(), ProductOperationTypeEnum.REFUND.getCode(), exist.getActivityId());
            }
        }
    }

    @Override
    public List<AuctionDepositRefund> selectByActivityId(Long activityId) {
        return refundMapper.selectByActivityId(activityId);
    }
}
