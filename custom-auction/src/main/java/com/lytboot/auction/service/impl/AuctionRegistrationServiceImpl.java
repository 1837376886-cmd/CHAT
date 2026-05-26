package com.lytboot.auction.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lytboot.auction.domain.entity.AuctionActivity;
import com.lytboot.auction.domain.entity.AuctionDeposit;
import com.lytboot.auction.domain.entity.AuctionRegistration;
import com.lytboot.auction.domain.entity.AuctionSurvey;
import com.lytboot.auction.enums.*;
import com.lytboot.auction.mapper.AuctionActivityMapper;
import com.lytboot.auction.mapper.AuctionDepositMapper;
import com.lytboot.auction.mapper.AuctionRegistrationMapper;
import com.lytboot.auction.mapper.AuctionSurveyMapper;
import com.lytboot.auction.service.IAuctionRegistrationService;
import com.lytboot.auction.service.IProductOperationLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 报名记录Service实现
 */
@Service
public class AuctionRegistrationServiceImpl extends ServiceImpl<AuctionRegistrationMapper, AuctionRegistration> implements IAuctionRegistrationService {

    @Autowired
    private AuctionRegistrationMapper registrationMapper;

    @Autowired
    private AuctionActivityMapper activityMapper;

    @Autowired
    private AuctionDepositMapper depositMapper;

    @Autowired
    private AuctionSurveyMapper surveyMapper;

    @Autowired
    private IProductOperationLogService operationLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(Long activityId, Long userId, AuctionRegistration registration) {
        AuctionActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new RuntimeException("活动不存在");
        }

        AuctionRegistration exist = registrationMapper.selectByActivityAndUser(activityId, userId);
        if (exist != null) {
            throw new RuntimeException("您已报名该活动");
        }

        registration.setActivityId(activityId);
        registration.setUserId(userId);
        registration.setRegisterTime(LocalDateTime.now());
        registration.setAuditStatus(AuditStatusEnum.PENDING.getCode());
        registration.setIsEligible("0");
        registrationMapper.insert(registration);

        // 生成保证金记录
        AuctionDeposit deposit = new AuctionDeposit();
        deposit.setActivityId(activityId);
        deposit.setUserId(userId);
        deposit.setDepositAmount(activity.getDepositPrice());
        depositMapper.insert(deposit);

        // 如果是资源包类型，生成踏勘记录
        if (ProductTypeEnum.RESOURCE.getCode().equals(activity.getProductType())) {
            AuctionSurvey survey = new AuctionSurvey();
            survey.setActivityId(activityId);
            survey.setUserId(userId);
            survey.setSurveyStatus(SurveyStatusEnum.UNCONFIRMED.getCode());
            surveyMapper.insert(survey);
        }

        operationLogService.addLog(activity.getProductId(), ProductOperationTypeEnum.REGISTER.getCode(), activityId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditRegistration(Long id, String auditStatus, String auditRemark, Long auditBy) {
        AuctionRegistration registration = registrationMapper.selectById(id);
        if (registration == null) {
            throw new RuntimeException("报名记录不存在");
        }

        AuctionRegistration update = new AuctionRegistration();
        update.setId(id);
        update.setAuditStatus(auditStatus);
        update.setAuditRemark(auditRemark);
        update.setAuditBy(auditBy);
        update.setAuditTime(LocalDateTime.now());

        if (AuditStatusEnum.PASSED.getCode().equals(auditStatus)) {
            update.setRandomNickName(generateRandomNickName());
            // 报名审核通过即赋予出价资格（保证金状态由保证金记录单独跟踪）
            update.setIsEligible("1");
        }

        registrationMapper.updateById(update);

        AuctionActivity activity = activityMapper.selectById(registration.getActivityId());
        if (activity != null) {
            operationLogService.addLog(activity.getProductId(), ProductOperationTypeEnum.AUDIT.getCode(), registration.getActivityId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyDeposit(Long registrationId, String payType, String payVoucher) {
        AuctionRegistration registration = registrationMapper.selectById(registrationId);
        if (registration == null) {
            throw new RuntimeException("报名记录不存在");
        }

        // 只更新保证金记录
        AuctionDeposit deposit = depositMapper.selectByActivityAndUser(registration.getActivityId(), registration.getUserId());
        if (deposit != null) {
            AuctionDeposit depUpdate = new AuctionDeposit();
            depUpdate.setId(deposit.getId());
            depUpdate.setPayType(payType);
            depUpdate.setPayVoucher(payVoucher);
            depositMapper.updateById(depUpdate);
        }

        AuctionActivity activity = activityMapper.selectById(registration.getActivityId());
        if (activity != null) {
            operationLogService.addLog(activity.getProductId(), ProductOperationTypeEnum.DEPOSIT.getCode(), registration.getActivityId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditDeposit(Long id, String auditStatus, String auditRemark, Long auditBy) {
        AuctionDeposit deposit = depositMapper.selectById(id);
        if (deposit == null) {
            throw new RuntimeException("保证金记录不存在");
        }

        AuctionDeposit depUpdate = new AuctionDeposit();
        depUpdate.setId(id);
        depUpdate.setAuditStatus(auditStatus);
        depUpdate.setAuditRemark(auditRemark);
        depUpdate.setAuditBy(auditBy);
        depUpdate.setAuditTime(LocalDateTime.now());
        depositMapper.updateById(depUpdate);

        // 保证金审核通过且已缴纳，才赋予出价资格
        if (AuditStatusEnum.PASSED.getCode().equals(auditStatus)
                && "1".equals(deposit.getPayStatus())) {
            AuctionRegistration reg = registrationMapper.selectByActivityAndUser(deposit.getActivityId(), deposit.getUserId());
            if (reg != null && AuditStatusEnum.PASSED.getCode().equals(reg.getAuditStatus())) {
                AuctionRegistration regUpdate = new AuctionRegistration();
                regUpdate.setId(reg.getId());
                regUpdate.setIsEligible("1");
                registrationMapper.updateById(regUpdate);
            }
        }

        AuctionActivity activity = activityMapper.selectById(deposit.getActivityId());
        if (activity != null) {
            operationLogService.addLog(activity.getProductId(), ProductOperationTypeEnum.AUDIT.getCode(), deposit.getActivityId());
        }
    }

    @Override
    public AuctionRegistration selectByActivityAndUser(Long activityId, Long userId) {
        return registrationMapper.selectByActivityAndUser(activityId, userId);
    }

    @Override
    public List<AuctionRegistration> selectByActivityId(Long activityId) {
        return registrationMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuctionRegistration>()
                .eq(AuctionRegistration::getActivityId, activityId)
        );
    }

    private String generateRandomNickName() {
        String[] prefixes = {"竞拍", "猎手", "赢家", "先锋", "勇者", "豪客", "达人", "行家"};
        return prefixes[(int)(Math.random() * prefixes.length)] + UUID.randomUUID().toString().substring(0, 6);
    }
}
