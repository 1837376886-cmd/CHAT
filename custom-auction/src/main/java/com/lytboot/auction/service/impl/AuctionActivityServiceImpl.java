package com.lytboot.auction.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lytboot.auction.domain.entity.*;
import com.lytboot.auction.enums.*;
import com.lytboot.auction.mapper.*;
import com.lytboot.auction.scheduler.AuctionStateScheduler;
import com.lytboot.auction.service.IAuctionActivityService;
import com.lytboot.auction.service.IAuctionRegistrationService;
import com.lytboot.auction.service.IProductOperationLogService;
import com.lytboot.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 拍卖活动Service实现
 */
@Service
public class AuctionActivityServiceImpl extends ServiceImpl<AuctionActivityMapper, AuctionActivity> implements IAuctionActivityService {

    @Autowired
    private AuctionActivityMapper activityMapper;

    @Autowired
    private AuctionBidMapper bidMapper;

    @Autowired
    private AuctionMessageMapper messageMapper;

    @Autowired
    private AuctionStateScheduler stateScheduler;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private IProductOperationLogService operationLogService;

    @Autowired
    private IAuctionRegistrationService registrationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishActivity(AuctionActivity activity) {
        activity.setActivityCode(generateActivityCode());
        activity.setStatus(AuctionActivityStatusEnum.PENDING_PUBLISH.getCode());
        activity.setAuditStatus(AuditStatusEnum.PENDING.getCode());
        activity.setCurrentPrice(activity.getStartingPrice());
        activity.setBidCount(0);
        activity.setViewCount(0);
        activityMapper.insert(activity);

        operationLogService.addLog(activity.getProductId(), ProductOperationTypeEnum.SUBMIT.getCode(), activity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditActivity(Long id, String auditStatus, String auditRemark, Long auditBy) {
        AuctionActivity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new RuntimeException("活动不存在");
        }

        AuctionActivity update = new AuctionActivity();
        update.setId(id);
        update.setAuditStatus(auditStatus);
        update.setAuditRemark(auditRemark);

        if (AuditStatusEnum.PASSED.getCode().equals(auditStatus)) {
            update.setStatus(AuctionActivityStatusEnum.PENDING_START.getCode());
        }

        activityMapper.updateById(update);

        operationLogService.addLog(activity.getProductId(), ProductOperationTypeEnum.AUDIT.getCode(), id);

        if (AuditStatusEnum.PASSED.getCode().equals(auditStatus)) {
            registerTransitionsAfterAudit(id);
        }
    }

    @Override
    public void registerTransitionsAfterAudit(Long activityId) {
        stateScheduler.registerAllTransitions(activityId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelActivity(Long id) {
        AuctionActivity exist = activityMapper.selectById(id);
        if (exist == null) {
            throw new RuntimeException("活动不存在");
        }
        AuctionActivity activity = new AuctionActivity();
        activity.setId(id);
        activity.setStatus(AuctionActivityStatusEnum.CANCELLED.getCode());
        activityMapper.updateById(activity);
        stateScheduler.cancelAllTasks(id);

        operationLogService.addLog(exist.getProductId(), ProductOperationTypeEnum.AUDIT.getCode(), id);
    }

    @Override
    public void updateStatus(Long id, String status) {
        activityMapper.updateStatus(id, status);
        redisTemplate.opsForValue().set("auction:status:" + id, status);
    }

    @Override
    public void updateActualEndTime(Long id, LocalDateTime actualEndTime) {
        activityMapper.updateActualEndTime(id, actualEndTime);
        redisTemplate.opsForValue().set("auction:actual_end:" + id, String.valueOf(java.sql.Timestamp.valueOf(actualEndTime).getTime()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void placeBid(Long activityId, Long userId, String userName, BigDecimal bidPrice) {
        String lockKey = "auction:bid:lock:" + activityId;
        String lockValue = UUID.randomUUID().toString();
        boolean locked = false;
        try {
            locked = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, 10, TimeUnit.SECONDS));
            if (!locked) {
                throw new RuntimeException("出价过于频繁，请稍后重试");
            }

            // 在锁内校验出价资格，防止并发窗口下资格状态变化
            AuctionRegistration registration = registrationService.selectByActivityAndUser(activityId, userId);
            if (registration == null || !"1".equals(registration.getIsEligible())) {
                throw new RuntimeException("您没有出价资格");
            }

            AuctionActivity activity = activityMapper.selectById(activityId);
            if (activity == null) {
                throw new RuntimeException("活动不存在");
            }
            if (!AuctionActivityStatusEnum.BIDDING.getCode().equals(activity.getStatus())) {
                throw new RuntimeException("活动不在竞拍中状态");
            }

            BigDecimal minPrice = activity.getCurrentPrice() != null ? activity.getCurrentPrice() : activity.getStartingPrice();
            if (bidPrice.compareTo(minPrice.add(activity.getIncrStep())) < 0) {
                throw new RuntimeException("出价必须大于当前价格 + 加价幅度");
            }

            activityMapper.updateCurrentPrice(activityId, bidPrice, userId);

            AuctionBid bid = new AuctionBid();
            bid.setActivityId(activityId);
            bid.setUserId(userId);
            bid.setRealName(userName);
            bid.setRandomNickName(registration.getRandomNickName());
            bid.setBidPrice(bidPrice);
            bid.setBidTime(LocalDateTime.now());
            bidMapper.insert(bid);

            AuctionMessage message = new AuctionMessage();
            message.setActivityId(activityId);
            message.setContent("用户 " + registration.getRandomNickName() + " 出价 " + bidPrice + " 元");
            messageMapper.insert(message);

            operationLogService.addLog(activity.getProductId(), ProductOperationTypeEnum.BID.getCode(), activityId);

            // 延时竞拍判断（默认最后5分钟内出价延时5分钟）
            LocalDateTime effectiveEnd = activity.getActualEndTime() != null ? activity.getActualEndTime() : activity.getAuctionEndTime();
            if (effectiveEnd != null) {
                long minutesToEnd = java.time.Duration.between(LocalDateTime.now(), effectiveEnd).toMinutes();
                if (minutesToEnd <= 5 && minutesToEnd > 0) {
                    LocalDateTime newEndTime = effectiveEnd.plusMinutes(5);
                    stateScheduler.postponeAuctionEnd(activityId, newEndTime);

                    AuctionMessage delayMessage = new AuctionMessage();
                    delayMessage.setActivityId(activityId);
                    delayMessage.setContent("竞拍延时，结束时间延长至 " + newEndTime);
                    messageMapper.insert(delayMessage);
                }
            }
        } finally {
            if (locked) {
                String currentValue = redisTemplate.opsForValue().get(lockKey);
                if (lockValue.equals(currentValue)) {
                    redisTemplate.delete(lockKey);
                }
            }
        }
    }

    @Override
    public void incrementViewCount(Long id) {
        activityMapper.incrementViewCount(id);
    }

    private String generateActivityCode() {
        return "A" + DateUtils.dateTimeNow("yyyyMMddHHmmss") + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }
}
