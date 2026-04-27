package com.ruoyi.chat.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.chat.domain.entity.CsSession;
import com.ruoyi.chat.mapper.CsSessionMapper;
import com.ruoyi.chat.service.ICsSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客服会话Service实现
 *
 * @author ruoyi
 */
@Service
public class CsSessionServiceImpl extends ServiceImpl<CsSessionMapper, CsSession> implements ICsSessionService {

    @Autowired
    private CsSessionMapper csSessionMapper;

    @Override
    public List<CsSession> selectActiveSessionsByCsUserId(Long csUserId) {
        return csSessionMapper.selectActiveSessionsByCsUserId(csUserId);
    }

    @Override
    public CsSession selectActiveSessionByVisitorId(Long visitorId) {
        return csSessionMapper.selectActiveSessionByVisitorId(visitorId);
    }

    @Override
    public void closeSession(Long sessionId) {
        CsSession session = new CsSession();
        session.setId(sessionId);
        session.setStatus(CsSession.Status.ENDED);
        session.setEndTime(LocalDateTime.now());
        csSessionMapper.updateById(session);
    }

    @Override
    public CsSession createSession(Long visitorId, Long csUserId) {
        CsSession session = new CsSession();
        session.setVisitorId(visitorId);
        session.setCsUserId(csUserId);
        session.setStatus(CsSession.Status.ACTIVE);
        session.setStartTime(LocalDateTime.now());
        csSessionMapper.insert(session);
        return session;
    }

    @Override
    public List<CsSession> selectSessionsByVisitorId(Long visitorId) {
        return csSessionMapper.selectSessionsByVisitorId(visitorId);
    }

    @Override
    public void incrementUnreadCount(Long sessionId) {
        csSessionMapper.incrementUnreadCount(sessionId);
    }

    @Override
    public void resetUnreadCount(Long sessionId) {
        CsSession session = new CsSession();
        session.setId(sessionId);
        session.setCsUnreadCount(0);
        csSessionMapper.updateById(session);
    }
}
