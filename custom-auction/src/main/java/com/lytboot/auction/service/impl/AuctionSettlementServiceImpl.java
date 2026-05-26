package com.lytboot.auction.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lytboot.auction.domain.entity.AuctionActivity;
import com.lytboot.auction.domain.entity.AuctionDeal;
import com.lytboot.auction.domain.entity.AuctionSettlement;
import com.lytboot.auction.enums.AuditStatusEnum;
import com.lytboot.auction.enums.ProductOperationTypeEnum;
import com.lytboot.auction.enums.SettlementStatusEnum;
import com.lytboot.auction.mapper.AuctionActivityMapper;
import com.lytboot.auction.mapper.AuctionDealMapper;
import com.lytboot.auction.mapper.AuctionSettlementMapper;
import com.lytboot.auction.service.IAuctionDepositRefundService;
import com.lytboot.auction.service.IAuctionSettlementService;
import com.lytboot.auction.service.IProductOperationLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 尾款结算申请Service实现
 */
@Service
public class AuctionSettlementServiceImpl extends ServiceImpl<AuctionSettlementMapper, AuctionSettlement> implements IAuctionSettlementService {

    @Autowired
    private AuctionSettlementMapper settlementMapper;

    @Autowired
    private AuctionDealMapper dealMapper;

    @Autowired
    private AuctionActivityMapper activityMapper;

    @Autowired
    private IAuctionDepositRefundService depositRefundService;

    @Autowired
    private IProductOperationLogService operationLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitSettlement(AuctionSettlement settlement) {
        AuctionDeal deal = dealMapper.selectById(settlement.getDealId());
        if (deal == null) {
            throw new RuntimeException("成交记录不存在");
        }

        settlement.setActivityId(deal.getActivityId());
        settlement.setUserId(deal.getUserId());
        settlement.setAuditStatus(AuditStatusEnum.PENDING.getCode());

        // 计算实际支付金额 = 结算金额 - 保证金抵扣
        BigDecimal actualPay = settlement.getSettlementAmount();
        if (settlement.getDepositDeductAmount() != null) {
            actualPay = actualPay.subtract(settlement.getDepositDeductAmount());
        }
        settlement.setActualPayAmount(actualPay);

        settlementMapper.insert(settlement);

        AuctionActivity activity = activityMapper.selectById(deal.getActivityId());
        if (activity != null) {
            operationLogService.addLog(activity.getProductId(), ProductOperationTypeEnum.SUBMIT.getCode(), deal.getActivityId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditSettlement(Long id, String auditStatus, String auditRemark, Long auditBy) {
        AuctionSettlement settlement = new AuctionSettlement();
        settlement.setId(id);
        settlement.setAuditStatus(auditStatus);
        settlement.setAuditRemark(auditRemark);
        settlement.setAuditBy(auditBy);
        settlement.setAuditTime(LocalDateTime.now());

        AuctionSettlement exist = settlementMapper.selectById(id);

        if (AuditStatusEnum.PASSED.getCode().equals(auditStatus)) {
            settlement.setSettlementTime(LocalDateTime.now());
            // 更新成交记录为已结算
            if (exist != null) {
                AuctionDeal dealUpdate = new AuctionDeal();
                dealUpdate.setId(exist.getDealId());
                dealUpdate.setSettlementStatus(SettlementStatusEnum.SETTLED.getCode());
                dealMapper.updateById(dealUpdate);
                // 尾款结算审核通过，给中标人生成保证金退款申请
                depositRefundService.generateRefundForDealUser(exist.getActivityId(), exist.getUserId());
            }
        }

        settlementMapper.updateById(settlement);

        if (exist != null) {
            AuctionActivity activity = activityMapper.selectById(exist.getActivityId());
            if (activity != null) {
                operationLogService.addLog(activity.getProductId(), ProductOperationTypeEnum.SETTLEMENT_AUDIT.getCode(), exist.getActivityId());
            }
        }
    }

    @Override
    public AuctionSettlement selectByDealId(Long dealId) {
        return settlementMapper.selectByDealId(dealId);
    }
}
