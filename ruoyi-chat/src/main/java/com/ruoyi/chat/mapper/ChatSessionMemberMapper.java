package com.ruoyi.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.chat.domain.entity.ChatSessionMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会话成员Mapper接口
 *
 * @author ruoyi
 * @date 2024-01-01
 */
@Mapper
public interface ChatSessionMemberMapper extends BaseMapper<ChatSessionMember> {

    /**
     * 根据会话ID查询成员列表
     *
     * @param sessionId 会话ID
     * @return 成员列表
     */
    List<ChatSessionMember> selectMembersBySessionId(@Param("sessionId") String sessionId);

    /**
     * 根据会话ID和用户ID查询成员信息
     *
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @return 成员信息
     */
    ChatSessionMember selectMemberBySessionIdAndUserId(@Param("sessionId") String sessionId, @Param("userId") Long userId);

    /**
     * 根据会话ID和排除的用户ID查询另一个成员信息
     *
     * @param sessionId 会话ID
     * @param excludeUserId 排除的用户ID
     * @return 成员信息
     */
    ChatSessionMember selectOtherMemberInPrivateSession(@Param("sessionId") String sessionId, @Param("excludeUserId") Long excludeUserId);

    /**
     * 批量插入会话成员
     *
     * @param members 成员列表
     * @return 插入数量
     */
    int insertBatchMembers(@Param("members") List<ChatSessionMember> members);

    /**
     * 检查用户是否已读消息
     *
     * @param messageId 消息ID
     * @param userId 用户ID
     * @return 检查结果
     */
    int checkUserReadMessage(@Param("messageId") String messageId, @Param("userId") Long userId);
}