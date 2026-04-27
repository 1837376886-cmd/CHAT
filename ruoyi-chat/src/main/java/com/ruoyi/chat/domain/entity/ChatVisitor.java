package com.ruoyi.chat.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 访客信息实体类
 *
 * @author ruoyi
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("chat_visitor")
public class ChatVisitor implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 访客Token
     */
    @TableField("visitor_token")
    private String visitorToken;

    /**
     * 访客昵称
     */
    @TableField("nickname")
    private String nickname;

    /**
     * IP地址
     */
    @TableField("ip")
    private String ip;

    /**
     * 浏览器UA
     */
    @TableField("user_agent")
    private String userAgent;

    /**
     * 来源页面URL
     */
    @TableField("source_page")
    private String sourcePage;

    /**
     * 已绑定的sys_user.id
     */
    @TableField("bound_user_id")
    private Long boundUserId;

    /**
     * 最近一次接待该访客的客服userId
     */
    @TableField("last_cs_user_id")
    private Long lastCsUserId;

    /**
     * 最近一次会话结束时间
     */
    @TableField("last_session_end_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastSessionEndTime;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
