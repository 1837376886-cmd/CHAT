package com.ruoyi.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.chat.domain.entity.CsSession;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 客服会话Mapper接口
 *
 * @author ruoyi
 */
public interface CsSessionMapper extends BaseMapper<CsSession> {

    /**
     * 查询客服当前进行中的会话列表
     */
    List<CsSession> selectActiveSessionsByCsUserId(@Param("csUserId") Long csUserId);

    /**
     * 查询访客当前进行中的会话
     */
    CsSession selectActiveSessionByVisitorId(@Param("visitorId") Long visitorId);

    /**
     * 查询访客的历史会话列表
     */
    List<CsSession> selectSessionsByVisitorId(@Param("visitorId") Long visitorId);

    /**
     * 根据客服ID统计进行中会话数
     */
    int countActiveSessionsByCsUserId(@Param("csUserId") Long csUserId);

    /**
     * 增加客服未读消息数
     */
    void incrementUnreadCount(@Param("sessionId") Long sessionId);
}
