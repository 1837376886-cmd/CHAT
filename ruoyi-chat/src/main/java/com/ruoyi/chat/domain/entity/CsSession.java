package com.ruoyi.chat.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客服会话实体类
 *
 * @author ruoyi
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("cs_session")
public class CsSession implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 访客ID
     */
    @TableField("visitor_id")
    private Long visitorId;

    /**
     * 客服用户ID
     */
    @TableField("cs_user_id")
    private Long csUserId;

    /**
     * 状态：0-已结束，1-进行中，2-等待中
     */
    @TableField("status")
    private Integer status;

    /**
     * 客服未读消息数
     */
    @TableField("cs_unread_count")
    private Integer csUnreadCount;

    /**
     * 会话开始时间
     */
    @TableField("start_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /**
     * 会话结束时间
     */
    @TableField("end_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 状态枚举
     */
    public static class Status {
        public static final int ENDED = 0;
        public static final int ACTIVE = 1;
        public static final int WAITING = 2;
    }
}
