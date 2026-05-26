package com.lytboot.auction.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lytboot.auction.domain.entity.AuctionActivity;
import com.lytboot.auction.domain.entity.AuctionSurvey;
import com.lytboot.auction.enums.ProductOperationTypeEnum;
import com.lytboot.auction.mapper.AuctionActivityMapper;
import com.lytboot.auction.mapper.AuctionSurveyMapper;
import com.lytboot.auction.service.IAuctionSurveyService;
import com.lytboot.auction.service.IProductOperationLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 踏勘记录Service实现
 */
@Service
public class AuctionSurveyServiceImpl extends ServiceImpl<AuctionSurveyMapper, AuctionSurvey> implements IAuctionSurveyService {

    @Autowired
    private AuctionSurveyMapper surveyMapper;

    @Autowired
    private AuctionActivityMapper activityMapper;

    @Autowired
    private IProductOperationLogService operationLogService;

    @Override
    public void confirmSurvey(Long id, String surveyStatus, String surveyFile, String remark) {
        AuctionSurvey exist = surveyMapper.selectById(id);

        AuctionSurvey survey = new AuctionSurvey();
        survey.setId(id);
        survey.setSurveyStatus(surveyStatus);
        survey.setSurveyFile(surveyFile);
        survey.setRemark(remark);
        survey.setUpdateTime(LocalDateTime.now());
        surveyMapper.updateById(survey);

        if (exist != null) {
            AuctionActivity activity = activityMapper.selectById(exist.getActivityId());
            if (activity != null) {
                operationLogService.addLog(activity.getProductId(), ProductOperationTypeEnum.SURVEY.getCode(), exist.getActivityId());
            }
        }
    }

    @Override
    public AuctionSurvey selectByActivityAndUser(Long activityId, Long userId) {
        return surveyMapper.selectByActivityAndUser(activityId, userId);
    }
}
