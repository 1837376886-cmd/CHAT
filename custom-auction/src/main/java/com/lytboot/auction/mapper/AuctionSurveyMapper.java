package com.lytboot.auction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lytboot.auction.domain.entity.AuctionSurvey;
import org.apache.ibatis.annotations.Param;

/**
 * 踏勘记录Mapper接口
 */
public interface AuctionSurveyMapper extends BaseMapper<AuctionSurvey> {

    AuctionSurvey selectByActivityAndUser(@Param("activityId") Long activityId, @Param("userId") Long userId);
}
