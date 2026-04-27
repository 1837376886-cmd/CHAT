package com.ruoyi.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ruoyi.chat.domain.entity.ChatVisitor;

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
     * 登录后按IP绑定访客记录
     */
    int bindByLogin(Long userId, String ip);

    /**
     * 根据IP查询未绑定的访客列表
     */
    List<ChatVisitor> selectUnboundByIp(String ip, int days);
}
