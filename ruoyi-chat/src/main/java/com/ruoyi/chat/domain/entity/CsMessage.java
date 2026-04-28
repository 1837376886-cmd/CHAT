package com.ruoyi.chat.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客服消息实体类
 *
 * @author ruoyi
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("cs_message")
public class CsMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 客服会话ID
     */
    @TableField("session_id")
    private Long sessionId;

    /**
     * 发送者类型：1-访客，2-客服，3-系统
     */
    @TableField("from_type")
    private Integer fromType;

    /**
     * 发送者用户ID（客服时有效）
     */
    @TableField("from_user_id")
    private Long fromUserId;

    /**
     * 消息内容
     */
    @TableField("content")
    private String content;

    /**
     * 消息类型：1-文本，2-图片
     */
    @TableField("msg_type")
    private Integer msgType;

    /**
     * 发送者名称（不存库，仅用于展示）
     */
    @TableField(exist = false)
    private String senderName;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 发送者类型枚举
     */
    public static class FromType {
        public static final int VISITOR = 1;
        public static final int CS = 2;
        public static final int SYSTEM = 3;
    }

    /**
     * 消息类型枚举
     */
    public static class MsgType {
        public static final int TEXT = 1;
        public static final int IMAGE = 2;
    }
}
