package com.lytboot.auction.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lytboot.auction.domain.entity.AuctionSurvey;

/**
 * 踏勘记录Service接口
 */
public interface IAuctionSurveyService extends IService<AuctionSurvey> {

    /**
     * 确认踏勘
     */
    void confirmSurvey(Long id, String surveyStatus, String surveyFile, String remark);

    /**
     * 根据活动和用户查询
     */
    AuctionSurvey selectByActivityAndUser(Long activityId, Long userId);
}
