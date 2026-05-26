package com.lytboot.auction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lytboot.auction.domain.entity.AuctionSettlement;
import org.apache.ibatis.annotations.Param;

/**
 * 尾款结算申请Mapper接口
 */
public interface AuctionSettlementMapper extends BaseMapper<AuctionSettlement> {

    AuctionSettlement selectByDealId(@Param("dealId") Long dealId);
}
