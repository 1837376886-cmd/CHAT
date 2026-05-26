package com.lytboot.auction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lytboot.auction.domain.entity.AuctionBid;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 出价记录Mapper接口
 */
public interface AuctionBidMapper extends BaseMapper<AuctionBid> {

    int getBidCount(@Param("activityId") Long activityId);

    List<AuctionBid> selectByActivityId(@Param("activityId") Long activityId);

    AuctionBid selectHighestBid(@Param("activityId") Long activityId);
}
