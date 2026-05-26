package com.lytboot.auction.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 拍卖活动实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("auction_activity")
public class AuctionActivity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("parent_id")
    private Long parentId;

    @TableField("product_id")
    private Long productId;

    @TableField("activity_code")
    private String activityCode;

    @TableField("activity_name")
    private String activityName;

    @TableField("estimated_price")
    private BigDecimal estimatedPrice;

    @TableField("activity_start_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime activityStartTime;

    @TableField("activity_end_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime activityEndTime;

    @TableField("register_start_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime registerStartTime;

    @TableField("register_end_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime registerEndTime;

    @TableField("take_item_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime takeItemTime;

    @TableField("take_item_type")
    private String takeItemType;

    @TableField("auction_start_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime auctionStartTime;

    @TableField("auction_end_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime auctionEndTime;

    @TableField("actual_end_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actualEndTime;

    @TableField("starting_price")
    private BigDecimal startingPrice;

    @TableField("deposit_price")
    private BigDecimal depositPrice;

    @TableField("incr_step")
    private BigDecimal incrStep;

    @TableField("time_step")
    private Integer timeStep;

    @TableField("tax_point")
    private String taxPoint;

    @TableField("commission")
    private String commission;

    @TableField("current_price")
    private BigDecimal currentPrice;

    @TableField("current_price_user_id")
    private Long currentPriceUserId;

    @TableField("bid_count")
    private Integer bidCount;

    @TableField("view_count")
    private Integer viewCount;

    @TableField("status")
    private String status;

    @TableField("estimated_size")
    private BigDecimal estimatedSize;

    @TableField("unit")
    private String unit;

    @TableField("survey_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime surveyTime;

    @TableField("platform_manager")
    private String platformManager;

    @TableField("platform_manager_phone")
    private String platformManagerPhone;

    @TableField("audit_status")
    private String auditStatus;

    @TableField("audit_remark")
    private String auditRemark;

    @TableField("file")
    private String file;

    @TableField("product_type")
    private String productType;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @TableField("remark")
    private String remark;
}
