package com.lytboot.auction.scheduler;

import com.lytboot.auction.domain.entity.AuctionActivity;
import com.lytboot.auction.domain.entity.AuctionDeal;
import com.lytboot.auction.domain.entity.AuctionMessage;
import com.lytboot.auction.domain.entity.StateScheduleTask;
import com.lytboot.auction.enums.*;
import com.lytboot.auction.mapper.AuctionActivityMapper;
import com.lytboot.auction.mapper.AuctionStateScheduleMapper;
import com.lytboot.auction.service.IAuctionBidService;
import com.lytboot.auction.service.IAuctionDealService;
import com.lytboot.auction.service.IAuctionDepositRefundService;
import com.lytboot.auction.service.IAuctionMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 拍卖活动状态流转调度器
 *
 * <p>负责驱动拍卖活动按时间自动流转状态，共4个阶段：</p>
 * <ul>
 *   <li>待启动 → 报名中（到报名开始时间自动触发）</li>
 *   <li>报名中 → 待拍中（到报名结束时间自动触发）</li>
 *   <li>待拍中 → 竞拍中（到竞拍开始时间自动触发）</li>
 *   <li>竞拍中 → 已结束（到竞拍结束时间自动触发，含延时逻辑）</li>
 * </ul>
 *
 * <p>实现机制：</p>
 * <ul>
 *   <li>每个流转点对应数据库一条 {@link StateScheduleTask} 记录</li>
 *   <li>使用 {@link ScheduledThreadPoolExecutor} 将任务注册为延时执行</li>
 *   <li>服务重启时从数据库恢复未执行的待执行任务</li>
 *   <li>Redis 同步写入状态，供用户侧快速读取</li>
 * </ul>
 */
@Service
@Slf4j
public class AuctionStateScheduler implements InitializingBean, DisposableBean {

    @Autowired
    private AuctionStateScheduleMapper scheduleMapper;

    @Autowired
    private AuctionActivityMapper activityMapper;

    @Autowired
    private IAuctionBidService bidService;

    @Autowired
    private IAuctionDealService dealService;

    @Autowired
    private IAuctionDepositRefundService depositRefundService;

    @Autowired
    private IAuctionMessageService messageService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${auction.scheduler.pool-size:8}")
    private int poolSize;

    /** 调度线程池，所有状态流转任务在此排队执行 */
    private ScheduledThreadPoolExecutor executor;

    /** 内存中的任务映射，key = activityId:transitionType，用于取消或覆盖任务 */
    private final ConcurrentHashMap<String, ScheduledFuture<?>> futureMap = new ConcurrentHashMap<>();

    /**
     * 初始化调度线程池
     */
    @PostConstruct
    public void initExecutor() {
        this.executor = new ScheduledThreadPoolExecutor(poolSize, r -> {
            Thread t = new Thread(r, "auction-scheduler-" + r.hashCode());
            t.setDaemon(true);
            return t;
        });
        executor.setRemoveOnCancelPolicy(true);
        log.info("[状态调度器] 线程池初始化，核心线程数: {}", poolSize);
    }

    /**
     * 服务启动时从数据库恢复未执行的任务。
     * <p>已超期的任务立即提交到线程池执行，未超期的重新注册为延时任务。</p>
     */
    @Override
    public void afterPropertiesSet() {
        List<StateScheduleTask> pendingTasks = scheduleMapper.selectByStatus(ScheduleTaskStatusEnum.PENDING.getCode());
        int loaded = 0, executed = 0;

        for (StateScheduleTask task : pendingTasks) {
            if (task.getScheduledTime().isBefore(LocalDateTime.now()) || task.getScheduledTime().isEqual(LocalDateTime.now())) {
                executor.execute(() -> executeTransition(task));
                executed++;
            } else {
                registerTask(task);
                loaded++;
            }
        }

        log.info("[状态调度器] 启动恢复完成：加载{}个任务，立即执行{}个超期任务", loaded, executed);
    }

    /**
     * 为指定活动注册全部4个状态流转任务。
     * <p>一般在活动审核通过后调用。如果某个时间点已过，则立即执行对应流转。</p>
     *
     * @param activityId 活动ID
     */
    public void registerAllTransitions(Long activityId) {
        AuctionActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            log.warn("[注册流转任务] 活动不存在: activityId={}", activityId);
            return;
        }

        long now = System.currentTimeMillis();
        int registered = 0;

        // 1. 待启动 → 报名中
        if (activity.getRegisterStartTime() != null) {
            long delay = java.sql.Timestamp.valueOf(activity.getRegisterStartTime()).getTime() - now;
            if (delay > 0) {
                doRegister(activityId, StateTransitionTypeEnum.PENDING_TO_REGISTERING.getCode(), activity.getRegisterStartTime());
                registered++;
            } else {
                // 已过期，立即执行
                executor.execute(() -> handlePendingToRegistering(activityId));
            }
        }

        // 2. 报名中 → 待拍中
        if (activity.getRegisterEndTime() != null) {
            long delay = java.sql.Timestamp.valueOf(activity.getRegisterEndTime()).getTime() - now;
            if (delay > 0) {
                doRegister(activityId, StateTransitionTypeEnum.REGISTERING_TO_PENDING_BID.getCode(), activity.getRegisterEndTime());
                registered++;
            }
        }

        // 3. 待拍中 → 竞拍中
        if (activity.getAuctionStartTime() != null) {
            long delay = java.sql.Timestamp.valueOf(activity.getAuctionStartTime()).getTime() - now;
            if (delay > 0) {
                doRegister(activityId, StateTransitionTypeEnum.PENDING_BID_TO_BIDDING.getCode(), activity.getAuctionStartTime());
                registered++;
            }
        }

        // 4. 竞拍中 → 已结束（优先取 actualEndTime，支持延时竞拍）
        LocalDateTime effectiveEndTime = activity.getActualEndTime() != null ? activity.getActualEndTime() : activity.getAuctionEndTime();
        if (effectiveEndTime != null) {
            long delay = java.sql.Timestamp.valueOf(effectiveEndTime).getTime() - now;
            if (delay > 0) {
                doRegister(activityId, StateTransitionTypeEnum.BIDDING_TO_ENDED.getCode(), effectiveEndTime);
                registered++;
            }
        }

        log.info("[注册流转任务] activityId={}, 成功注册{}个延时任务", activityId, registered);
    }

    /** 插入任务记录并注册到线程池 */
    private void doRegister(Long activityId, String transitionType, LocalDateTime scheduledTime) {
        StateScheduleTask task = new StateScheduleTask();
        task.setActivityId(activityId);
        task.setTransitionType(transitionType);
        task.setScheduledTime(scheduledTime);
        task.setStatus(ScheduleTaskStatusEnum.PENDING.getCode());
        scheduleMapper.insert(task);
        registerTask(task);
    }

    /** 对外暴露的任务调度入口，插入记录后注册到线程池 */
    public void scheduleTask(Long activityId, String transitionType, LocalDateTime scheduledTime) {
        StateScheduleTask task = new StateScheduleTask();
        task.setActivityId(activityId);
        task.setTransitionType(transitionType);
        task.setScheduledTime(scheduledTime);
        task.setStatus(ScheduleTaskStatusEnum.PENDING.getCode());
        scheduleMapper.insert(task);
        registerTask(task);
    }

    /**
     * 将任务注册到线程池。
     * <p>如果同一活动同一类型已有任务，先取消旧任务（覆盖逻辑）。</p>
     */
    private void registerTask(StateScheduleTask task) {
        long delay = java.sql.Timestamp.valueOf(task.getScheduledTime()).getTime() - System.currentTimeMillis();
        if (delay <= 0) {
            // 已超期，立即执行
            executor.execute(() -> executeTransition(task));
            return;
        }

        String key = task.getActivityId() + ":" + task.getTransitionType();
        ScheduledFuture<?> oldFuture = futureMap.remove(key);
        if (oldFuture != null) {
            oldFuture.cancel(false);
        }

        ScheduledFuture<?> future = executor.schedule(() -> executeTransition(task), delay, TimeUnit.MILLISECONDS);
        futureMap.put(key, future);
    }

    /** 取消指定活动的某个流转任务（内存 + 数据库） */
    public void cancelTask(Long activityId, String transitionType) {
        String key = activityId + ":" + transitionType;
        ScheduledFuture<?> future = futureMap.remove(key);
        if (future != null) {
            future.cancel(false);
        }
        scheduleMapper.updateStatusByActivityAndType(activityId, transitionType, ScheduleTaskStatusEnum.CANCELLED.getCode());
    }

    /** 取消指定活动的全部流转任务 */
    public void cancelAllTasks(Long activityId) {
        for (StateTransitionTypeEnum type : StateTransitionTypeEnum.values()) {
            cancelTask(activityId, type.getCode());
        }
    }

    /**
     * 竞拍延时：取消旧的"竞拍中→已结束"任务，用新的结束时间重新注册。
     * <p>用户出价触发延时时调用。</p>
     *
     * @param activityId  活动ID
     * @param newEndTime  新的实际结束时间
     */
    public void postponeAuctionEnd(Long activityId, LocalDateTime newEndTime) {
        AuctionActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            log.warn("[竞拍延时] 活动不存在: activityId={}", activityId);
            return;
        }

        cancelTask(activityId, StateTransitionTypeEnum.BIDDING_TO_ENDED.getCode());
        scheduleTask(activityId, StateTransitionTypeEnum.BIDDING_TO_ENDED.getCode(), newEndTime);

        AuctionActivity update = new AuctionActivity();
        update.setId(activityId);
        update.setActualEndTime(newEndTime);
        activityMapper.updateById(update);

        redisTemplate.opsForValue().set("auction:actual_end:" + activityId, String.valueOf(java.sql.Timestamp.valueOf(newEndTime).getTime()));
        log.info("[竞拍延时] activityId={}, newActualEndTime={}", activityId, newEndTime);
    }

    /**
     * 执行状态流转任务。
     * <p>根据 task.transitionType 分发到对应的 handle 方法，执行成功后更新任务状态为已执行。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void executeTransition(StateScheduleTask task) {
        log.info("[状态流转] 执行: activityId={}, type={}, scheduledTime={}", task.getActivityId(), task.getTransitionType(), task.getScheduledTime());

        try {
            switch (task.getTransitionType()) {
                case "1":
                    handlePendingToRegistering(task.getActivityId());
                    break;
                case "2":
                    handleRegisteringToPendingBid(task.getActivityId());
                    break;
                case "3":
                    handlePendingBidToBidding(task.getActivityId());
                    break;
                case "4":
                    handleBiddingToEnded(task.getActivityId());
                    break;
            }
            task.setStatus(ScheduleTaskStatusEnum.EXECUTED.getCode());
        } catch (Exception e) {
            log.error("[状态流转] 执行失败: taskId={}", task.getId(), e);
        } finally {
            if (task.getId() != null) {
                scheduleMapper.updateStatus(task.getId(), task.getStatus());
            }
        }
    }

    /** 阶段1：待启动 → 报名中 */
    private void handlePendingToRegistering(Long activityId) {
        AuctionActivity activity = activityMapper.selectById(activityId);
        if (activity == null || !AuctionActivityStatusEnum.PENDING_START.getCode().equals(activity.getStatus())) {
            log.warn("[待启动→报名中] 条件不满足: activityId={}, status={}", activityId, activity != null ? activity.getStatus() : "null");
            return;
        }
        activityMapper.updateStatus(activityId, AuctionActivityStatusEnum.REGISTERING.getCode());
        redisTemplate.opsForValue().set("auction:status:" + activityId, AuctionActivityStatusEnum.REGISTERING.getCode());
        log.info("[待启动→报名中] activityId={}", activityId);

        messageService.sendSystemMessage(activityId, "活动开始报名");
        chainRegisterNext(activityId, StateTransitionTypeEnum.REGISTERING_TO_PENDING_BID.getCode(), activity.getRegisterEndTime());
    }

    /** 阶段2：报名中 → 待拍中（结束报名） */
    private void handleRegisteringToPendingBid(Long activityId) {
        AuctionActivity activity = activityMapper.selectById(activityId);
        if (activity == null || !AuctionActivityStatusEnum.REGISTERING.getCode().equals(activity.getStatus())) {
            log.warn("[报名中→结束报名] 条件不满足: activityId={}, status={}", activityId, activity != null ? activity.getStatus() : "null");
            return;
        }
        activityMapper.updateStatus(activityId, AuctionActivityStatusEnum.PENDING_BID.getCode());
        redisTemplate.opsForValue().set("auction:status:" + activityId, AuctionActivityStatusEnum.PENDING_BID.getCode());
        log.info("[报名中→结束报名] activityId={}", activityId);

        messageService.sendSystemMessage(activityId, "报名已截止，等待竞拍开始");
        // 自动链式注册下一个任务：待拍中 → 竞拍中
        chainRegisterNext(activityId, StateTransitionTypeEnum.PENDING_BID_TO_BIDDING.getCode(), activity.getAuctionStartTime());
    }

    /** 阶段3：待拍中 → 竞拍中（竞拍开始） */
    private void handlePendingBidToBidding(Long activityId) {
        AuctionActivity activity = activityMapper.selectById(activityId);
        if (activity == null || !AuctionActivityStatusEnum.PENDING_BID.getCode().equals(activity.getStatus())) {
            log.warn("[结束报名→竞拍中] 条件不满足: activityId={}, status={}", activityId, activity != null ? activity.getStatus() : "null");
            return;
        }
        activityMapper.updateStatus(activityId, AuctionActivityStatusEnum.BIDDING.getCode());
        redisTemplate.opsForValue().set("auction:status:" + activityId, AuctionActivityStatusEnum.BIDDING.getCode());
        log.info("[结束报名→竞拍中] activityId={}", activityId);

        messageService.sendSystemMessage(activityId, "竞拍开始");
        // 自动链式注册下一个任务：竞拍中 → 已结束
        chainRegisterNext(activityId, StateTransitionTypeEnum.BIDDING_TO_ENDED.getCode(), activity.getAuctionEndTime());
    }

    /** 阶段4：竞拍中 → 已结束（结算阶段） */
    private void handleBiddingToEnded(Long activityId) {
        AuctionActivity activity = activityMapper.selectById(activityId);
        if (activity == null || !AuctionActivityStatusEnum.BIDDING.getCode().equals(activity.getStatus())) {
            log.warn("[竞拍中→已结束] 条件不满足: activityId={}, status={}", activityId, activity != null ? activity.getStatus() : "null");
            return;
        }

        // 再次校验实际结束时间（防延时竞拍时旧任务误触发）
        LocalDateTime effectiveEndTime = activity.getActualEndTime() != null ? activity.getActualEndTime() : activity.getAuctionEndTime();
        if (effectiveEndTime != null && effectiveEndTime.isAfter(LocalDateTime.now())) {
            log.info("[竞拍中→已结束] 实际结束时间还未到，跳过: activityId={}, effectiveEndTime={}", activityId, effectiveEndTime);
            return;
        }

        activityMapper.updateStatus(activityId, AuctionActivityStatusEnum.ENDED.getCode());
        redisTemplate.opsForValue().set("auction:status:" + activityId, AuctionActivityStatusEnum.ENDED.getCode());

        int bidCount = bidService.getBidCount(activityId);

        if (bidCount == 0) {
            // 无人出价 → 流拍，给所有已缴纳保证金的人发起退款
            activityMapper.updateStatus(activityId, AuctionActivityStatusEnum.FLOW.getCode());
            depositRefundService.generateRefundForFlow(activityId);
            redisTemplate.opsForValue().set("auction:status:" + activityId, AuctionActivityStatusEnum.FLOW.getCode());
            messageService.sendSystemMessage(activityId, "竞拍流拍，无人出价");
            log.info("[竞拍流拍] activityId={}, 无人出价", activityId);
        } else {
            // 有出价 → 成交，生成成交记录并给非中标人发起保证金退款
            AuctionDeal deal = dealService.createDeal(activityId);
            depositRefundService.generateRefundForNonWinners(activityId, deal.getUserId());
            redisTemplate.opsForValue().set("auction:status:" + activityId, AuctionActivityStatusEnum.DEAL.getCode());
            redisTemplate.opsForValue().set("auction:result:" + activityId, String.valueOf(deal.getId()));
            messageService.sendSystemMessage(activityId, "竞拍成交，成交人：" + deal.getUserId());
            log.info("[竞拍成交] activityId={}, dealId={}", activityId, deal.getId());
        }
    }

    /**
     * 链式兜底：当前阶段流转完成后，如果下一阶段任务还没注册，自动补上。
     * <p>防止数据库任务丢失导致后续阶段无法执行。</p>
     */
    private void chainRegisterNext(Long activityId, String nextType, LocalDateTime nextScheduledTime) {
        if (nextScheduledTime == null) return;

        StateScheduleTask exist = scheduleMapper.selectPending(activityId, String.valueOf(nextType));
        if (exist != null) {
            return;
        }

        long delay = java.sql.Timestamp.valueOf(nextScheduledTime).getTime() - System.currentTimeMillis();
        if (delay > 0) {
            doRegister(activityId, nextType, nextScheduledTime);
            log.info("[链式兜底] 补注册下一个任务: activityId={}, type={}, scheduledTime={}", activityId, nextType, nextScheduledTime);
        }
    }

    /** 应用关闭时立即终止所有未执行的调度任务 */
    @Override
    public void destroy() {
        executor.shutdownNow();
        log.info("[状态调度器] 已关闭");
    }
}
