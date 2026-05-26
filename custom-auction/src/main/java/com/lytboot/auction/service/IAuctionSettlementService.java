package com.lytboot.auction.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lytboot.auction.domain.entity.AuctionSettlement;

/**
 * 尾款结算申请Service接口
 */
public interface IAuctionSettlementService extends IService<AuctionSettlement> {

    /**
     * 提交尾款结算申请
     */
    void submitSettlement(AuctionSettlement settlement);

    /**
     * 审核尾款结算
     */
    void auditSettlement(Long id, String auditStatus, String auditRemark, Long auditBy);

    /**
     * 根据成交记录查询
     */
    AuctionSettlement selectByDealId(Long dealId);
}
