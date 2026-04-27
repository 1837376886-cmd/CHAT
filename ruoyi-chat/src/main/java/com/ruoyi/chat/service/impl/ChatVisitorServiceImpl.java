package com.ruoyi.chat.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.chat.domain.entity.ChatVisitor;
import com.ruoyi.chat.mapper.ChatVisitorMapper;
import com.ruoyi.chat.service.IChatVisitorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 访客信息Service实现
 *
 * @author ruoyi
 */
@Service
public class ChatVisitorServiceImpl extends ServiceImpl<ChatVisitorMapper, ChatVisitor> implements IChatVisitorService {

    @Autowired
    private ChatVisitorMapper chatVisitorMapper;

    @Override
    public ChatVisitor getOrCreateVisitor(String visitorToken, String ip, String userAgent, String sourcePage) {
        ChatVisitor visitor = chatVisitorMapper.selectByVisitorToken(visitorToken);
        if (visitor != null) {
            return visitor;
        }
        visitor = new ChatVisitor();
        visitor.setVisitorToken(visitorToken);
        visitor.setNickname("访客" + UUID.randomUUID().toString().substring(0, 6));
        visitor.setIp(ip);
        visitor.setUserAgent(userAgent);
        visitor.setSourcePage(sourcePage);
        chatVisitorMapper.insert(visitor);
        return visitor;
    }

    @Override
    public ChatVisitor selectByVisitorToken(String visitorToken) {
        return chatVisitorMapper.selectByVisitorToken(visitorToken);
    }

    @Override
    public void updateLastCsUserId(Long visitorId, Long csUserId) {
        ChatVisitor visitor = new ChatVisitor();
        visitor.setId(visitorId);
        visitor.setLastCsUserId(csUserId);
        chatVisitorMapper.updateById(visitor);
    }

    @Override
    public int bindByLogin(Long userId, String ip) {
        return chatVisitorMapper.bindUserIdByIp(userId, ip, 90);
    }

    @Override
    public List<ChatVisitor> selectUnboundByIp(String ip, int days) {
        return chatVisitorMapper.selectByIpAndUnbound(ip, days);
    }
}
