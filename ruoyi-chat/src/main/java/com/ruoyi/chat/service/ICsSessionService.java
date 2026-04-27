package com.ruoyi.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ruoyi.chat.domain.entity.CsSession;

import java.util.List;

/**
 * 客服会话Service接口
 *
 * @author ruoyi
 */
public interface ICsSessionService extends IService<CsSession> {

    /**
     * 查询客服当前进行中的会话列表
     */
    List<CsSession> selectActiveSessionsByCsUserId(Long csUserId);

    /**
     * 查询访客当前进行中的会话
     */
    CsSession selectActiveSessionByVisitorId(Long visitorId);

    /**
     * 结束会话
     */
    void closeSession(Long sessionId);

    /**
     * 创建会话
     */
    CsSession createSession(Long visitorId, Long csUserId);

    /**
     * 查询访客的历史会话列表
     */
    List<CsSession> selectSessionsByVisitorId(Long visitorId);

    /**
     * 增加客服未读消息数
     */
    void incrementUnreadCount(Long sessionId);

    /**
     * 清零客服未读消息数
     */
    void resetUnreadCount(Long sessionId);
}
