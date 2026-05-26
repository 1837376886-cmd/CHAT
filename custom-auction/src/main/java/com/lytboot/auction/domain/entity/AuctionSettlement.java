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
 * 尾款结算申请实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("auction_settlement")
public class AuctionSettlement implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("deal_id")
    private Long dealId;

    @TableField("activity_id")
    private Long activityId;

    @TableField("user_id")
    private Long userId;

    @TableField("real_name")
    private String realName;

    @TableField("settlement_size")
    private BigDecimal settlementSize;

    @TableField("settlement_amount")
    private BigDecimal settlementAmount;

    @TableField("deposit_deduct_amount")
    private BigDecimal depositDeductAmount;

    @TableField("actual_pay_amount")
    private BigDecimal actualPayAmount;

    @TableField("pay_type")
    private String payType;

    @TableField("is_invoice")
    private String isInvoice;

    @TableField("invoice_voucher")
    private String invoiceVoucher;

    @TableField("pay_voucher")
    private String payVoucher;

    @TableField("pay_trade_no")
    private String payTradeNo;

    @TableField("pay_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime payTime;

    @TableField("audit_status")
    private String auditStatus;

    @TableField("audit_by")
    private Long auditBy;

    @TableField("audit_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime auditTime;

    @TableField("audit_remark")
    private String auditRemark;

    @TableField("settlement_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime settlementTime;

    @TableField("tax_price")
    private BigDecimal taxPrice;

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
