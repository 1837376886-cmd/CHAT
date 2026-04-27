package com.ruoyi.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.chat.domain.entity.CsMessage;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 客服消息Mapper接口
 *
 * @author ruoyi
 */
public interface CsMessageMapper extends BaseMapper<CsMessage> {

    /**
     * 根据会话ID查询消息列表
     */
    List<CsMessage> selectMessagesBySessionId(@Param("sessionId") Long sessionId);

    /**
     * 根据访客ID查询所有会话消息（历史消息）
     */
    List<CsMessage> selectMessagesByVisitorId(@Param("visitorId") Long visitorId);
}
