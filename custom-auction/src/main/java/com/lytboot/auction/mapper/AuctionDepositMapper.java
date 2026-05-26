package com.lytboot.auction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lytboot.auction.domain.entity.AuctionDeposit;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 保证金记录Mapper接口
 */
public interface AuctionDepositMapper extends BaseMapper<AuctionDeposit> {

    AuctionDeposit selectByActivityAndUser(@Param("activityId") Long activityId, @Param("userId") Long userId);

    List<AuctionDeposit> selectPaidByActivity(@Param("activityId") Long activityId);
}
