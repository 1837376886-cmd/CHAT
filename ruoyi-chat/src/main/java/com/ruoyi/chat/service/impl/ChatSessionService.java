package com.ruoyi.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.chat.domain.entity.ChatSession;
import com.ruoyi.chat.domain.entity.ChatSessionMember;
import com.ruoyi.chat.mapper.ChatSessionMapper;
import com.ruoyi.chat.mapper.ChatSessionMemberMapper;
import com.ruoyi.chat.service.IChatSessionService;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.system.service.ISysUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class ChatSessionService implements IChatSessionService {

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Autowired
    private ChatSessionMemberMapper chatSessionMemberMapper;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private RedisCache redisCache;

    @Override
    @Transactional
    public ChatSession createPrivateSession(Long userId1, Long userId2) {
        ChatSession existingSession = chatSessionMapper.selectPrivateSessionByUserIds(userId1, userId2);
        if (existingSession != null) {
            return existingSession;
        }

        String sessionId = UUID.randomUUID().toString().replace("-", "");
        ChatSession session = new ChatSession()
                .setSessionId(sessionId)
                .setSessionType(ChatSession.SessionType.PRIVATE)
                .setCreatorId(userId1)
                .setStatus(ChatSession.Status.ENABLED)
                .setCreateTime(LocalDateTime.now())
                .setUpdateTime(LocalDateTime.now());

        chatSessionMapper.insert(session);

        List<ChatSessionMember> members = new ArrayList<>();
        members.add(new ChatSessionMember()
                .setSessionId(sessionId)
                .setUserId(userId1)
                .setRole(ChatSessionMember.Role.MEMBER)
                .setStatus(ChatSessionMember.Status.NORMAL)
                .setJoinTime(LocalDateTime.now()));
        members.add(new ChatSessionMember()
                .setSessionId(sessionId)
                .setUserId(userId2)
                .setRole(ChatSessionMember.Role.MEMBER)
                .setStatus(ChatSessionMember.Status.NORMAL)
                .setJoinTime(LocalDateTime.now()));

        chatSessionMemberMapper.insertBatchMembers(members);
        log.info("创建私聊会话成功，sessionId: {}, userId1: {}, userId2: {}", sessionId, userId1, userId2);
        return session;
    }

    @Override
    @Transactional
    public ChatSession createGroupSession(Long creatorId, String sessionName, List<Long> memberIds) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        ChatSession session = new ChatSession()
                .setSessionId(sessionId)
                .setSessionType(ChatSession.SessionType.GROUP)
                .setSessionName(sessionName)
                .setCreatorId(creatorId)
                .setMaxMembers(500)
                .setStatus(ChatSession.Status.ENABLED)
                .setCreateTime(LocalDateTime.now())
                .setUpdateTime(LocalDateTime.now());

        chatSessionMapper.insert(session);

        List<ChatSessionMember> members = new ArrayList<>();
        members.add(new ChatSessionMember()
                .setSessionId(sessionId)
                .setUserId(creatorId)
                .setRole(ChatSessionMember.Role.OWNER)
                .setStatus(ChatSessionMember.Status.NORMAL)
                .setJoinTime(LocalDateTime.now()));

        for (Long memberId : memberIds) {
            if (!memberId.equals(creatorId)) {
                members.add(new ChatSessionMember()
                        .setSessionId(sessionId)
                        .setUserId(memberId)
                        .setRole(ChatSessionMember.Role.MEMBER)
                        .setStatus(ChatSessionMember.Status.NORMAL)
                        .setJoinTime(LocalDateTime.now()));
            }
        }

        chatSessionMemberMapper.insertBatchMembers(members);
        log.info("创建群聊会话成功，sessionId: {}, creatorId: {}, memberCount: {}", sessionId, creatorId, members.size());
        return session;
    }

    @Override
    public List<ChatSession> getUserSessions(Long userId) {
        List<ChatSession> sessions = chatSessionMapper.selectSessionsByUserId(userId);
        
        for (ChatSession session : sessions) {
            if (session.getSessionType() == ChatSession.SessionType.PRIVATE) {
                ChatSessionMember member = chatSessionMemberMapper.selectOtherMemberInPrivateSession(session.getSessionId(), userId);
                if (member != null) {
                    SysUser user = userService.selectUserById(member.getUserId());
                    if (user != null) {
                        session.setSessionName(user.getUserName());
                        session.setSessionAvatar(user.getAvatar());
                    }
                }
            }
        }
        
        return sessions;
    }

    @Override
    public ChatSession getSessionById(String sessionId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getSessionId, sessionId);
        return chatSessionMapper.selectOne(wrapper);
    }

    @Override
    @Transactional
    public boolean joinSession(String sessionId, Long userId) {
        ChatSession session = getSessionById(sessionId);
        if (session == null) {
            log.warn("会话不存在，sessionId: {}", sessionId);
            return false;
        }

        if (isUserInSession(sessionId, userId)) {
            log.warn("用户已在会话中，sessionId: {}, userId: {}", sessionId, userId);
            return true;
        }

        ChatSessionMember member = new ChatSessionMember()
                .setSessionId(sessionId)
                .setUserId(userId)
                .setRole(ChatSessionMember.Role.MEMBER)
                .setStatus(ChatSessionMember.Status.NORMAL)
                .setJoinTime(LocalDateTime.now());

        int result = chatSessionMemberMapper.insert(member);
        log.info("用户加入会话，sessionId: {}, userId: {}, result: {}", sessionId, userId, result > 0);
        return result > 0;
    }

    @Override
    @Transactional
    public boolean leaveSession(String sessionId, Long userId) {
        LambdaQueryWrapper<ChatSessionMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSessionMember::getSessionId, sessionId)
                .eq(ChatSessionMember::getUserId, userId);

        ChatSessionMember member = new ChatSessionMember()
                .setStatus(ChatSessionMember.Status.LEFT);

        int result = chatSessionMemberMapper.update(member, wrapper);
        log.info("用户离开会话，sessionId: {}, userId: {}, result: {}", sessionId, userId, result > 0);
        return result > 0;
    }

    @Override
    public List<ChatSessionMember> getSessionMembers(String sessionId) {
        return chatSessionMemberMapper.selectMembersBySessionId(sessionId);
    }

    @Override
    public boolean isUserInSession(String sessionId, Long userId) {
        ChatSessionMember member = chatSessionMemberMapper.selectMemberBySessionIdAndUserId(sessionId, userId);
        return member != null && member.getStatus().equals(ChatSessionMember.Status.NORMAL);
    }

    @Override
    public ChatSession getOrCreatePrivateSession(Long userId1, Long userId2) {
        ChatSession existingSession = chatSessionMapper.selectPrivateSessionByUserIds(userId1, userId2);
        if (existingSession != null) {
            return existingSession;
        }
        return createPrivateSession(userId1, userId2);
    }
    
    public void incrementUnreadCount(Long userId, String sessionId) {
        try {
            String key = "chat:unread:" + userId + ":" + sessionId;
            redisCache.increment(key, 1);
            redisCache.expire(key, 7, java.util.concurrent.TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("增加未读消息计数失败", e);
        }
    }
    
    public Long getUnreadCount(Long userId, String sessionId) {
        try {
            String key = "chat:unread:" + userId + ":" + sessionId;
            Integer count = redisCache.getCacheObject(key);
            return count != null ? count.longValue() : 0L;
        } catch (Exception e) {
            log.warn("获取未读消息计数失败", e);
            return 0L;
        }
    }
    
    public void clearUnreadCount(Long userId, String sessionId) {
        try {
            String key = "chat:unread:" + userId + ":" + sessionId;
            redisCache.deleteObject(key);
        } catch (Exception e) {
            log.warn("清除未读消息计数失败", e);
        }
    }
    
    public List<ChatSession> getUserSessionsWithUnreadCount(Long userId) {
        List<ChatSession> sessions = chatSessionMapper.selectSessionsByUserId(userId);
        
        for (ChatSession session : sessions) {
            if (session.getSessionType() == ChatSession.SessionType.PRIVATE) {
                ChatSessionMember member = chatSessionMemberMapper.selectOtherMemberInPrivateSession(session.getSessionId(), userId);
                if (member != null) {
                    SysUser user = userService.selectUserById(member.getUserId());
                    if (user != null) {
                        session.setSessionName(user.getUserName());
                        session.setSessionAvatar(user.getAvatar());
                    }
                }
            }
            
            Long unreadCount = getUnreadCount(userId, session.getSessionId());
            session.setUnreadCount(unreadCount.intValue());
        }
        
        return sessions;
    }
}
