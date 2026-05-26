package com.lytboot.auction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lytboot.auction.domain.entity.AuctionRegistration;
import org.apache.ibatis.annotations.Param;

/**
 * 报名记录Mapper接口
 */
public interface AuctionRegistrationMapper extends BaseMapper<AuctionRegistration> {

    AuctionRegistration selectByActivityAndUser(@Param("activityId") Long activityId, @Param("userId") Long userId);

    int updateEligible(@Param("id") Long id, @Param("isEligible") String isEligible);
}
