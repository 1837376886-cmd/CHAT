package com.lytboot.auction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lytboot.auction.domain.entity.AuctionDeal;
import org.apache.ibatis.annotations.Param;

/**
 * 成交记录Mapper接口
 */
public interface AuctionDealMapper extends BaseMapper<AuctionDeal> {

    AuctionDeal selectByActivityId(@Param("activityId") Long activityId);

    int updateSettlementStatus(@Param("id") Long id, @Param("status") String status);

    int markBreach(@Param("id") Long id);
}
