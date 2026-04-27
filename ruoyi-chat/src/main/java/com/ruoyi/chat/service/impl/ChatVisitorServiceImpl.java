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
    public ChatVisitor getOrCreateVisitor(String visitorToken, String ip, String userAgent, String sourcePage, String deviceFingerprint) {
        // 1. 优先按token查
        if (visitorToken != null && !visitorToken.isEmpty()) {
            ChatVisitor visitor = chatVisitorMapper.selectByVisitorToken(visitorToken);
            if (visitor != null) {
                return visitor;
            }
        }
        // 2. 按设备指纹查（区分同一IP下不同用户）
        if (deviceFingerprint != null && !deviceFingerprint.isEmpty()) {
            ChatVisitor visitor = chatVisitorMapper.selectByDeviceFingerprint(deviceFingerprint, 30);
            if (visitor != null) {
                return visitor;
            }
        }
        // 3. 按IP兜底（最近7天）
        ChatVisitor visitor = chatVisitorMapper.selectRecentByIp(ip, 7);
        if (visitor != null) {
            return visitor;
        }
        // 4. 创建新访客
        visitor = new ChatVisitor();
        visitor.setVisitorToken((visitorToken != null && !visitorToken.isEmpty()) ? visitorToken : UUID.randomUUID().toString().replace("-", ""));
        visitor.setNickname("访客" + UUID.randomUUID().toString().substring(0, 6));
        visitor.setIp(ip);
        visitor.setUserAgent(userAgent);
        visitor.setSourcePage(sourcePage);
        visitor.setDeviceFingerprint(deviceFingerprint);
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

    @Override
    public ChatVisitor selectRecentByIp(String ip, int days) {
        return chatVisitorMapper.selectRecentByIp(ip, days);
    }

    @Override
    public ChatVisitor selectByDeviceFingerprint(String deviceFingerprint, int days) {
        return chatVisitorMapper.selectByDeviceFingerprint(deviceFingerprint, days);
    }
}
