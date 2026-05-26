package com.lytboot.auction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lytboot.auction.domain.entity.StateScheduleTask;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 状态流转调度任务Mapper接口
 */
public interface AuctionStateScheduleMapper extends BaseMapper<StateScheduleTask> {

    List<StateScheduleTask> selectByStatus(@Param("status") String status);

    StateScheduleTask selectPending(@Param("activityId") Long activityId, @Param("type") String transitionType);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int updateStatusByActivityAndType(@Param("activityId") Long activityId, @Param("type") String type, @Param("status") String status);
}
