package com.lytboot.auction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lytboot.auction.domain.entity.AuctionActivity;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 拍卖活动Mapper接口
 */
public interface AuctionActivityMapper extends BaseMapper<AuctionActivity> {

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int updateActualEndTime(@Param("id") Long id, @Param("actualEndTime") LocalDateTime actualEndTime);

    int updateCurrentPrice(@Param("id") Long id, @Param("currentPrice") BigDecimal currentPrice, @Param("userId") Long userId);

    int incrementViewCount(@Param("id") Long id);
}
