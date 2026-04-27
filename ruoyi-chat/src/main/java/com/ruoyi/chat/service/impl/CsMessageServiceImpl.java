package com.ruoyi.chat.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.chat.domain.entity.CsMessage;
import com.ruoyi.chat.mapper.CsMessageMapper;
import com.ruoyi.chat.service.ICsMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 客服消息Service实现
 *
 * @author ruoyi
 */
@Service
public class CsMessageServiceImpl extends ServiceImpl<CsMessageMapper, CsMessage> implements ICsMessageService {

    @Autowired
    private CsMessageMapper csMessageMapper;

    @Override
    public void sendMessage(Long sessionId, Integer fromType, Long fromUserId, String content) {
        CsMessage message = new CsMessage();
        message.setSessionId(sessionId);
        message.setFromType(fromType);
        message.setFromUserId(fromUserId);
        message.setContent(content);
        message.setMsgType(CsMessage.MsgType.TEXT);
        csMessageMapper.insert(message);
    }

    @Override
    public void sendSystemMessage(Long sessionId, String content) {
        sendMessage(sessionId, CsMessage.FromType.SYSTEM, null, content);
    }

    @Override
    public List<CsMessage> selectMessagesBySessionId(Long sessionId) {
        return csMessageMapper.selectMessagesBySessionId(sessionId);
    }

    @Override
    public List<CsMessage> selectMessagesByVisitorId(Long visitorId) {
        return csMessageMapper.selectMessagesByVisitorId(visitorId);
    }
}
