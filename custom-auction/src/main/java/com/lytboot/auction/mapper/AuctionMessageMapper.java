package com.lytboot.auction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lytboot.auction.domain.entity.AuctionMessage;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 活动消息Mapper接口
 */
public interface AuctionMessageMapper extends BaseMapper<AuctionMessage> {

    List<AuctionMessage> selectByActivityId(@Param("activityId") Long activityId);
}
