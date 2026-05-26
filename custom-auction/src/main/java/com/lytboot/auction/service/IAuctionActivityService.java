package com.lytboot.auction.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lytboot.auction.domain.entity.AuctionActivity;

import java.time.LocalDateTime;

/**
 * 拍卖活动Service接口
 */
public interface IAuctionActivityService extends IService<AuctionActivity> {

    /**
     * 发布活动（第三方提交，待运营审核）
     */
    void publishActivity(AuctionActivity activity);

    /**
     * 运营审核活动
     */
    void auditActivity(Long id, String auditStatus, String auditRemark, Long auditBy);

    /**
     * 审核通过后注册状态流转任务
     */
    void registerTransitionsAfterAudit(Long activityId);

    /**
     * 取消活动
     */
    void cancelActivity(Long id);

    /**
     * 更新活动状态
     */
    void updateStatus(Long id, String status);

    /**
     * 更新实际结束时间
     */
    void updateActualEndTime(Long id, LocalDateTime actualEndTime);

    /**
     * 出价
     */
    void placeBid(Long activityId, Long userId, String userName, java.math.BigDecimal bidPrice);

    /**
     * 增加围观次数
     */
    void incrementViewCount(Long id);
}
