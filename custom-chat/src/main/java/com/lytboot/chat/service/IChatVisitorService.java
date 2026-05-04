package com.lytboot.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lytboot.chat.domain.entity.ChatVisitor;

import java.util.List;

/**
 * 访客信息Service接口
 *
 * @author ruoyi
 */
public interface IChatVisitorService extends IService<ChatVisitor> {

    /**
     * 根据访客Token获取或创建访客
     */
    ChatVisitor getOrCreateVisitor(String visitorToken, String ip, String userAgent, String sourcePage);

    /**
     * 根据访客Token查询
     */
    ChatVisitor selectByVisitorToken(String visitorToken);

    /**
     * 更新最后接待客服
     */
    void updateLastCsUserId(Long visitorId, Long csUserId);

    /**
     * 根据IP查询最近活跃的访客
     */
    ChatVisitor selectRecentByIp(String ip, int days);
}
