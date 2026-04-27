package com.ruoyi.chat.service;

import com.ruoyi.chat.domain.entity.ChatVisitor;
import com.ruoyi.chat.domain.entity.CsConfig;
import com.ruoyi.chat.domain.entity.CsMessage;
import com.ruoyi.chat.domain.entity.CsSession;
import com.ruoyi.chat.netty.ChatChannelHandler;
import com.ruoyi.chat.protocol.ChatMessage;
import com.ruoyi.chat.protocol.MessageType;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.system.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 客服分配引擎
 * 上次客服优先 -> 最小接待数 -> 满员话术兜底
 *
 * @author ruoyi
 */
@Service
public class CustomerServiceAllocationService {

    @Autowired
    private CustomerServiceRedisManager redisManager;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private IChatVisitorService chatVisitorService;

    @Autowired
    private ICsSessionService csSessionService;

    @Autowired
    private ICsMessageService csMessageService;

    @Autowired
    private ICsConfigService csConfigService;

    @Autowired
    private ChatChannelHandler chatChannelHandler;

    /**
     * 分配结果
     */
    public static class AllocationResult {
        private boolean success;
        private boolean waiting;
        private boolean waitingForLastCs;
        private Long sessionId;
        private Long csUserId;
        private String csNickname;
        private String message;

        public static AllocationResult success(Long sessionId, Long csUserId, String csNickname) {
            AllocationResult r = new AllocationResult();
            r.success = true;
            r.sessionId = sessionId;
            r.csUserId = csUserId;
            r.csNickname = csNickname;
            return r;
        }

        public static AllocationResult waiting(String message) {
            AllocationResult r = new AllocationResult();
            r.waiting = true;
            r.message = message;
            return r;
        }

        public static AllocationResult waitingForLastCs(String csNickname) {
            AllocationResult r = new AllocationResult();
            r.waitingForLastCs = true;
            r.csNickname = csNickname;
            r.message = "正在为您联系上次接待的客服 " + csNickname + "，请稍候...";
            return r;
        }

        public boolean isSuccess() { return success; }
        public boolean isWaiting() { return waiting; }
        public boolean isWaitingForLastCs() { return waitingForLastCs; }
        public Long getSessionId() { return sessionId; }
        public Long getCsUserId() { return csUserId; }
        public String getCsNickname() { return csNickname; }
        public String getMessage() { return message; }
    }

    /**
     * 执行分配
     */
    public AllocationResult allocate(ChatVisitor visitor) {
        // 1. 检查是否已有进行中会话
        CsSession activeSession = csSessionService.selectActiveSessionByVisitorId(visitor.getId());
        if (activeSession != null) {
            SysUser csUser = sysUserMapper.selectUserById(activeSession.getCsUserId());
            return AllocationResult.success(activeSession.getId(), activeSession.getCsUserId(),
                    csUser != null ? csUser.getNickName() : "客服");
        }

        // 2. 上次客服优先（选项B：宽松策略）
        Long lastCsUserId = visitor.getLastCsUserId();
        if (lastCsUserId != null) {
            SysUser lastCs = sysUserMapper.selectUserById(lastCsUserId);
            if (lastCs != null && Integer.valueOf(1).equals(lastCs.getIsCustomerService())) {
                String status = redisManager.getCsStatus(lastCsUserId);
                int activeCount = redisManager.getActiveCount(lastCsUserId);
                int maxSessions = getMaxSessions(lastCsUserId);

                if ("online".equals(status) && activeCount < maxSessions) {
                    // 上次客服在线且有容量 -> 直接分配
                    return createSessionAndAssign(visitor, lastCsUserId, lastCs.getNickName());
                }

                // 上次客服离线或已满 -> 直接走默认分配，不等待
            }
        }

        // 3. 检查是否有挂起该访客的客服上线了（重连场景）
        String visitorToken = visitor.getVisitorToken();
        List<Long> onlineCsIds = redisManager.getOnlineCsUserIds();
        for (Long csId : onlineCsIds) {
            String pending = redisManager.getPending(csId);
            if (visitorToken.equals(pending)) {
                SysUser cs = sysUserMapper.selectUserById(csId);
                redisManager.clearPending(csId);
                return createSessionAndAssign(visitor, csId, cs != null ? cs.getNickName() : "客服");
            }
        }

        // 4. 默认分配：在线且接待数最少的客服
        AllocationResult defaultResult = defaultAllocate(visitor);
        if (defaultResult != null) {
            return defaultResult;
        }

        // 5. 全部满员 -> 话术兜底，加入等待集合
        redisManager.addToWaiting(visitor.getVisitorToken());
        return AllocationResult.waiting("当前咨询繁忙，请稍候，客服稍后将为您服务");
    }

    /**
     * 默认分配（最小接待数）
     */
    private AllocationResult defaultAllocate(ChatVisitor visitor) {
        List<Long> onlineCsIds = redisManager.getOnlineCsUserIds();
        if (onlineCsIds.isEmpty()) {
            return null;
        }

        Long targetCsId = null;
        int minActive = Integer.MAX_VALUE;

        for (Long csId : onlineCsIds) {
            int active = redisManager.getActiveCount(csId);
            int max = getMaxSessions(csId);
            if (active < max && active < minActive) {
                minActive = active;
                targetCsId = csId;
            }
        }

        if (targetCsId == null) {
            return null;
        }

        SysUser cs = sysUserMapper.selectUserById(targetCsId);
        return createSessionAndAssign(visitor, targetCsId, cs != null ? cs.getNickName() : "客服");
    }

    /**
     * 创建会话并分配
     */
    private AllocationResult createSessionAndAssign(ChatVisitor visitor, Long csUserId, String csNickname) {
        CsSession session = csSessionService.createSession(visitor.getId(), csUserId);
        redisManager.incrementActiveCount(csUserId);
        redisManager.setVisitorSession(visitor.getVisitorToken(), session.getId(), csUserId);
        chatVisitorService.updateLastCsUserId(visitor.getId(), csUserId);

        // 读取客服配置的默认回复语
        CsConfig config = csConfigService.getOrCreateDefault(csUserId);
        String welcomeMsg = config.getAutoReply();
        if (welcomeMsg == null || welcomeMsg.isEmpty()) {
            welcomeMsg = "客服 " + csNickname + " 已接入，请问有什么可以帮您？";
        }
        String displayMsg = "自动回复：" + welcomeMsg;

        // 保存为客服消息并推送给访客
        csMessageService.sendMessage(session.getId(), CsMessage.FromType.CS, csUserId, displayMsg);
        ChatMessage pushMsg = new ChatMessage();
        pushMsg.setType(MessageType.CS_CHAT);
        pushMsg.setSessionId(String.valueOf(session.getId()));
        pushMsg.setContent(displayMsg);
        pushMsg.setFromUserId(csUserId);
        pushMsg.setFromUserNickname(csNickname);
        pushMsg.setTimestamp(new java.util.Date());
        pushMsg.setMessageId(java.util.UUID.randomUUID().toString());
        chatChannelHandler.sendMessageToVisitor(visitor.getVisitorToken(), pushMsg);

        // 通知客服有新会话
        ChatMessage notice = new ChatMessage();
        notice.setType(MessageType.SYSTEM_NOTICE);
        notice.setContent("新访客接入");
        notice.setMessageId(java.util.UUID.randomUUID().toString());
        chatChannelHandler.sendMessageToUser(csUserId, notice);

        return AllocationResult.success(session.getId(), csUserId, csNickname);
    }

    /**
     * 获取客服最大接待数
     */
    private int getMaxSessions(Long csUserId) {
        int max = redisManager.getMaxSessions(csUserId);
        if (max <= 0) {
            CsConfig config = csConfigService.getOrCreateDefault(csUserId);
            max = config.getMaxSessions() != null ? config.getMaxSessions() : 5;
            redisManager.setCsStatus(csUserId, redisManager.getCsStatus(csUserId), max);
        }
        return max;
    }

    /**
     * 结束会话后尝试消费等待集合
     */
    public void tryConsumeWaitingAfterClose(Long csUserId) {
        String visitorToken = redisManager.popFromWaiting();
        if (visitorToken == null) {
            return;
        }

        ChatVisitor visitor = chatVisitorService.selectByVisitorToken(visitorToken);
        if (visitor == null) {
            return;
        }

        // 检查该客服是否还有容量
        int active = redisManager.getActiveCount(csUserId);
        int max = getMaxSessions(csUserId);
        if (active >= max) {
            // 没有容量了，把访客加回去
            redisManager.addToWaiting(visitorToken);
            return;
        }

        SysUser cs = sysUserMapper.selectUserById(csUserId);
        AllocationResult result = createSessionAndAssign(visitor, csUserId,
                cs != null ? cs.getNickName() : "客服");

        // TODO: 通过WebSocket推送分配成功消息给访客
        // 这里暂时不处理WebSocket推送，留给消息路由层处理
    }

    /**
     * 客服上线时处理挂起的访客
     */
    public void processPendingOnCsOnline(Long csUserId) {
        String visitorToken = redisManager.getPending(csUserId);
        if (visitorToken == null) {
            return;
        }

        ChatVisitor visitor = chatVisitorService.selectByVisitorToken(visitorToken);
        if (visitor == null) {
            redisManager.clearPending(csUserId);
            return;
        }

        int active = redisManager.getActiveCount(csUserId);
        int max = getMaxSessions(csUserId);
        if (active < max) {
            redisManager.clearPending(csUserId);
            SysUser cs = sysUserMapper.selectUserById(csUserId);
            createSessionAndAssign(visitor, csUserId, cs != null ? cs.getNickName() : "客服");
        }
    }
}
