package com.lytboot.auction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lytboot.auction.domain.entity.AuctionDepositRefund;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 保证金退款记录Mapper接口
 */
public interface AuctionDepositRefundMapper extends BaseMapper<AuctionDepositRefund> {

    List<AuctionDepositRefund> selectByActivityId(@Param("activityId") Long activityId);
}
