package com.ruoyi.chat.service;

import com.ruoyi.chat.domain.entity.ChatVisitor;
import com.ruoyi.chat.domain.entity.CsSession;
import com.ruoyi.chat.netty.ChatChannelHandler;
import com.ruoyi.chat.protocol.ChatMessage;
import com.ruoyi.chat.protocol.MessageType;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.system.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 客服心跳超时检查定时任务
 * 客服异常断线（关闭浏览器、网络断开）超过5分钟后，自动标记为离线并结束会话
 *
 * @author ruoyi
 */
@Component
public class CsHeartbeatCheckTask {

    private static final Logger logger = LoggerFactory.getLogger(CsHeartbeatCheckTask.class);

    /**
     * 心跳超时时间：5分钟（毫秒）
     */
    private static final long HEARTBEAT_TIMEOUT_MS = 5 * 60 * 1000L;

    @Autowired
    private CustomerServiceRedisManager redisManager;

    @Autowired
    private ICsSessionService csSessionService;

    @Autowired
    private ICsMessageService csMessageService;

    @Autowired
    private IChatVisitorService chatVisitorService;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private ChatChannelHandler chatChannelHandler;

    @Autowired
    private CustomerServiceAllocationService allocationService;

    /**
     * 每1分钟检查一次客服心跳
     */
    @Scheduled(fixedRate = 60 * 1000)
    public void checkCsHeartbeat() {
        try {
            logger.info("开始执行客服心跳检查任务");
            Collection<String> keys = redisManager.getAllCsStatusKeys();
            if (keys == null || keys.isEmpty()) {
                return;
            }

            long now = System.currentTimeMillis();
            for (String key : keys) {
                Long csUserId = extractCsUserId(key);
                if (csUserId == null) {
                    continue;
                }

                String status = redisManager.getCsStatus(csUserId);
                if (!"online".equals(status)) {
                    continue;
                }

                Long lastHeartbeat = redisManager.getLastHeartbeat(csUserId);
                if (lastHeartbeat == null) {
                    continue;
                }

                if (now - lastHeartbeat > HEARTBEAT_TIMEOUT_MS) {
                    handleCsTimeout(csUserId);
                }
            }
        } catch (Exception e) {
            logger.error("客服心跳检查任务执行异常", e);
        }
    }

    /**
     * 处理客服心跳超时
     */
    private void handleCsTimeout(Long csUserId) {
        SysUser csUser = sysUserMapper.selectUserById(csUserId);
        String csName = csUser != null ? csUser.getNickName() : String.valueOf(csUserId);
        logger.info("客服心跳超时，自动下线: {} ({})", csName, csUserId);

        // 标记为离线
        redisManager.setCsStatus(csUserId, "offline", redisManager.getMaxSessions(csUserId), 0);

        // 结束所有进行中的会话并通知访客
        List<CsSession> activeSessions = csSessionService.selectActiveSessionsByCsUserId(csUserId);
        for (CsSession session : activeSessions) {
            csSessionService.closeSession(session.getId());
            csMessageService.sendSystemMessage(session.getId(), "客服已离线，会话已结束");

            try {
                ChatVisitor visitor = chatVisitorService.getById(session.getVisitorId());
                if (visitor != null) {
                    ChatMessage systemMsg = new ChatMessage(MessageType.SYSTEM_NOTICE);
                    systemMsg.setContent("客服已离线，会话已结束");
                    systemMsg.setMessageId(UUID.randomUUID().toString());
                    chatChannelHandler.sendMessageToVisitor(visitor.getVisitorToken(), systemMsg);
                }
            } catch (Exception e) {
                logger.warn("推送客服下线通知失败, sessionId={}", session.getId(), e);
            }

            // 尝试消费等待集合（每个会话结束后都尝试）
            allocationService.tryConsumeWaitingAfterClose(csUserId);
        }
    }

    private Long extractCsUserId(String redisKey) {
        String prefix = "cs:status:";
        if (redisKey.startsWith(prefix)) {
            try {
                return Long.valueOf(redisKey.substring(prefix.length()));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
