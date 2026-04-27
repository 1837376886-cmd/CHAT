package com.ruoyi.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ruoyi.chat.domain.entity.CsMessage;

import java.util.List;

/**
 * 客服消息Service接口
 *
 * @author ruoyi
 */
public interface ICsMessageService extends IService<CsMessage> {

    /**
     * 发送消息
     */
    void sendMessage(Long sessionId, Integer fromType, Long fromUserId, String content);

    /**
     * 发送系统消息
     */
    void sendSystemMessage(Long sessionId, String content);

    /**
     * 查询会话消息列表
     */
    List<CsMessage> selectMessagesBySessionId(Long sessionId);

    /**
     * 根据访客ID查询所有历史消息
     */
    List<CsMessage> selectMessagesByVisitorId(Long visitorId);
}
