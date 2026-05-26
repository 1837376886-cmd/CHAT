
-- 商品表
CREATE TABLE IF NOT EXISTS product_item (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    product_code    VARCHAR(30) NOT NULL COMMENT '商品编码，自动生成',
    product_name    VARCHAR(50) NOT NULL COMMENT '商品名称',
    category_id     CHAR(5) DEFAULT '' COMMENT '分类ID',
    cover_img       VARCHAR(100) NOT NULL COMMENT '封面图',
    item_img        JSON NOT NULL COMMENT '商品图列表',
    province        VARCHAR(30) NOT NULL COMMENT '省',
    city            VARCHAR(30) NOT NULL COMMENT '市',
    address         VARCHAR(100) NOT NULL COMMENT '详细地址',
    contact_person  VARCHAR(30) NOT NULL COMMENT '联系人',
    contact_phone   VARCHAR(30) NOT NULL COMMENT '联系电话',
    apply_status    CHAR(1) DEFAULT '1' COMMENT '应用状态(1拍卖 2商城)',
    source          VARCHAR(30) COMMENT '来源用户',
    product_file    JSON NOT NULL COMMENT '附件（商品信息）',
    create_by       BIGINT COMMENT '创建者',
    create_time     DATETIME DEFAULT NULL COMMENT '创建时间',
    update_by       BIGINT COMMENT '更新者',
    update_time     DATETIME DEFAULT NULL COMMENT '更新时间',
    remark          VARCHAR(500) DEFAULT '' COMMENT '备注'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='商品表';

-- 商品内容详情表
CREATE TABLE IF NOT EXISTS product_content (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    product_id      BIGINT NOT NULL COMMENT '商品ID',
    content         LONGTEXT NOT NULL COMMENT '内容',
    create_by       BIGINT COMMENT '创建者',
    create_time     DATETIME DEFAULT NULL COMMENT '创建时间',
    update_by       BIGINT COMMENT '更新者',
    update_time     DATETIME DEFAULT NULL COMMENT '更新时间',
    remark          VARCHAR(500) DEFAULT '' COMMENT '备注'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='商品内容详情表';

-- 商品操作记录表
CREATE TABLE IF NOT EXISTS product_operation_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    product_id      BIGINT NOT NULL COMMENT '商品ID',
    operation_type  VARCHAR(30) NOT NULL COMMENT '操作类型(提交/审核/报名/缴纳/踏勘/出价/成交/退款/结算审核等)',
    activity_id     BIGINT COMMENT '活动ID',
    mall_id         BIGINT COMMENT '商城ID',
    old_info        JSON NOT NULL COMMENT '旧信息',
    new_info        JSON NOT NULL COMMENT '新信息',
    create_by       BIGINT COMMENT '创建者',
    create_time     DATETIME DEFAULT NULL COMMENT '创建时间',
    update_by       BIGINT COMMENT '更新者',
    update_time     DATETIME DEFAULT NULL COMMENT '更新时间',
    remark          VARCHAR(500) DEFAULT '' COMMENT '备注'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='商品操作记录表';

-- 拍卖活动表
CREATE TABLE IF NOT EXISTS auction_activity (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    parent_id           BIGINT COMMENT '父ID（拆分活动用）',
    product_id          BIGINT NOT NULL COMMENT '商品ID',
    activity_code       VARCHAR(30) NOT NULL COMMENT '活动编码，自动生成',
    activity_name       VARCHAR(50) NOT NULL COMMENT '活动名称',
    estimated_price     DECIMAL(20,2) NOT NULL COMMENT '预估价格',
    activity_start_time DATETIME NOT NULL COMMENT '活动开始时间',
    activity_end_time   DATETIME NOT NULL COMMENT '活动结束时间',
    register_start_time DATETIME NOT NULL COMMENT '报名开始时间',
    register_end_time   DATETIME NOT NULL COMMENT '报名结束时间',
    take_item_time      DATETIME NOT NULL COMMENT '提货结束时间',
    take_item_type      VARCHAR(10) DEFAULT '2' COMMENT '提货方式(1线上验货 2线下验货 3其它)',
    auction_start_time  DATETIME NOT NULL COMMENT '竞拍开始时间',
    auction_end_time    DATETIME NOT NULL COMMENT '竞拍结束时间',
    actual_end_time     DATETIME NOT NULL COMMENT '竞拍实际结束时间（倒计时使用）',
    starting_price      DECIMAL(20,2) NOT NULL COMMENT '起拍价',
    deposit_price       DECIMAL(20,2) NOT NULL COMMENT '保证金，审核时可更改',
    incr_step           DECIMAL(10,0) NOT NULL COMMENT '加价幅度',
    time_step           INT NOT NULL COMMENT '延时周期(单位分钟)',
    tax_point           VARCHAR(10) NOT NULL COMMENT '税点（百分点）',
    commission          VARCHAR(10) NOT NULL COMMENT '佣金（百分点），参与保证金退款计算',
    current_price       DECIMAL(20,2) COMMENT '当前出价',
    current_price_user_id BIGINT COMMENT '当前出价人ID',
    bid_count           INT DEFAULT 0 COMMENT '出价次数',
    view_count          INT DEFAULT 0 COMMENT '围观次数',
    status              CHAR(1) DEFAULT '0' COMMENT '状态(0待发布 1待启动 2报名中 3待拍中 4竞拍中 5已结束 6已成交 7流拍 8已取消)',
    estimated_size      DECIMAL(10,2) NOT NULL COMMENT '预估大小',
    unit                VARCHAR(30) NOT NULL COMMENT '单位（编码），字典表',
    survey_time         DATETIME COMMENT '踏勘时间',
    platform_manager    VARCHAR(30) NOT NULL COMMENT '平台负责人，审核时选择',
    platform_manager_phone VARCHAR(30) NOT NULL COMMENT '负责人联系方式',
    audit_status        CHAR(1) DEFAULT '0' COMMENT '审批状态(0待审批 1通过 2拒绝)',
    audit_remark        VARCHAR(500) DEFAULT '' COMMENT '审批原因',
    file                JSON COMMENT '附件信息，审核时上传',
    product_type        CHAR(1) DEFAULT '1' COMMENT '类型（1货品类、2资源包、3二手设备）',
    create_by           BIGINT COMMENT '创建者',
    create_time         DATETIME DEFAULT NULL COMMENT '创建时间',
    update_by           BIGINT COMMENT '更新者',
    update_time         DATETIME DEFAULT NULL COMMENT '更新时间',
    remark              VARCHAR(500) DEFAULT '' COMMENT '备注'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='拍卖活动表';

-- 报名记录表
CREATE TABLE IF NOT EXISTS auction_registration (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    activity_id         BIGINT NOT NULL COMMENT '拍卖活动ID',
    user_id             BIGINT NOT NULL COMMENT '用户ID',
    real_name           VARCHAR(30) NOT NULL COMMENT '真实姓名',
    phone               VARCHAR(30) NOT NULL COMMENT '联系电话',
    register_time       DATETIME NOT NULL COMMENT '报名时间',
    audit_status        CHAR(1) DEFAULT '0' COMMENT '审批状态(0待审批 1通过 2拒绝)',
    audit_by            BIGINT COMMENT '审批人ID',
    audit_time          DATETIME COMMENT '审批时间',
    audit_remark        VARCHAR(500) DEFAULT '' COMMENT '审批备注',
    random_nick_name    VARCHAR(30) DEFAULT '' COMMENT '随机昵称（审批通过后分配，出价时匿名展示）',
    is_eligible         CHAR(1) DEFAULT '0' COMMENT '是否有出价资格(0否 1是)',
    registration_file   JSON COMMENT '报名材料',
    create_by           BIGINT COMMENT '创建者',
    create_time         DATETIME DEFAULT NULL COMMENT '创建时间',
    update_by           BIGINT COMMENT '更新者',
    update_time         DATETIME DEFAULT NULL COMMENT '更新时间',
    remark              VARCHAR(500) DEFAULT '' COMMENT '备注'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='报名记录表';

-- 保证金记录表
CREATE TABLE IF NOT EXISTS auction_deposit (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    activity_id     BIGINT NOT NULL COMMENT '拍卖活动ID',
    user_id         BIGINT NOT NULL COMMENT '用户ID',
    deposit_amount  DECIMAL(20,2) NOT NULL COMMENT '保证金金额',
    pay_type        CHAR(1) NOT NULL COMMENT '缴纳方式(1线上缴纳 2线下缴纳)',
    pay_status      CHAR(1) DEFAULT '0' COMMENT '缴纳状态(0未缴纳 1已缴纳 2已退还 3待退款)',
    is_deduct      CHAR(1) DEFAULT '1' COMMENT '是否抵扣保证金(0抵扣 1不抵扣)',
    pay_voucher     VARCHAR(255) DEFAULT '' COMMENT '线下支付凭证',
    pay_trade_no     VARCHAR(100) DEFAULT '' COMMENT '线上支付流水号',
    pay_time        DATETIME COMMENT '支付时间',
    audit_status        CHAR(1) DEFAULT '0' COMMENT '审批状态(0待审批 1通过 2拒绝)',
    audit_by            BIGINT COMMENT '审批人ID',
    audit_time          DATETIME COMMENT '审批时间',
    audit_remark        VARCHAR(500) DEFAULT '' COMMENT '审批备注',
    refund_time     DATETIME COMMENT '退还时间',
    refund_trade_no VARCHAR(100) DEFAULT '' COMMENT '退还流水号',
    create_by           BIGINT COMMENT '创建者',
    create_time     DATETIME DEFAULT NULL COMMENT '创建时间',
    update_by           BIGINT COMMENT '更新者',
    update_time     DATETIME DEFAULT NULL COMMENT '更新时间',
    remark          VARCHAR(500) DEFAULT '' COMMENT '备注'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='保证金记录表';

-- 保证金退款记录表
CREATE TABLE IF NOT EXISTS auction_deposit_refund (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    activity_id         BIGINT NOT NULL COMMENT '拍卖活动ID',
    user_id             BIGINT NOT NULL COMMENT '用户ID',
    deposit_amount      DECIMAL(20,2) NOT NULL COMMENT '保证金金额',
    refund_price        DECIMAL(20,2) NOT NULL COMMENT '应退款金额',
    actual_refund_price DECIMAL(20,2) NOT NULL COMMENT '实际退款金额',
    pay_type            CHAR(1) NOT NULL COMMENT '退款方式(1线上缴纳 2线下缴纳)',
    refund_voucher      VARCHAR(255) DEFAULT '' COMMENT '退款凭证（线下缴纳的时候不可为空）',
    create_by           BIGINT COMMENT '创建者',
    create_time         DATETIME DEFAULT NULL COMMENT '创建时间',
    update_by           BIGINT COMMENT '更新者',
    update_time         DATETIME DEFAULT NULL COMMENT '更新时间',
    remark              VARCHAR(500) DEFAULT '' COMMENT '备注'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='保证金退款记录表';

-- 出价记录表
CREATE TABLE IF NOT EXISTS auction_bid (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    activity_id         BIGINT NOT NULL COMMENT '拍卖活动ID',
    user_id             BIGINT NOT NULL COMMENT '用户ID',
    real_name           VARCHAR(30) NOT NULL DEFAULT '' COMMENT '出价用户名称（真实姓名）',
    random_nick_name    VARCHAR(30) NOT NULL DEFAULT '' COMMENT '随机昵称（报名时分配，出价时匿名展示）',
    bid_price           DECIMAL(20,2) NOT NULL COMMENT '出价金额',
    bid_time            DATETIME NOT NULL COMMENT '出价时间',
    create_by           BIGINT COMMENT '创建者',
    create_time         DATETIME DEFAULT NULL COMMENT '创建时间',
    update_by           BIGINT COMMENT '更新者',
    update_time         DATETIME DEFAULT NULL COMMENT '更新时间',
    remark              VARCHAR(500) DEFAULT '' COMMENT '备注'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='出价记录表';

-- 踏勘记录表
CREATE TABLE IF NOT EXISTS auction_survey (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    activity_id         BIGINT NOT NULL COMMENT '拍卖活动ID',
    user_id             BIGINT NOT NULL COMMENT '报名用户ID',
    real_name           VARCHAR(30) NOT NULL COMMENT '出价用户名称（真实姓名）',
    survey_user_phone   VARCHAR(30) NOT NULL COMMENT '踏勘负责人联系方式',
    survey_user_name    VARCHAR(30) NOT NULL COMMENT '踏勘负责人',
    survey_status       CHAR(1) DEFAULT '0' COMMENT '踏勘状态(0未确认 1已确认)',
    survey_type         VARCHAR(50) DEFAULT '' COMMENT '踏勘类型',
    survey_file         VARCHAR(500) DEFAULT '' COMMENT '踏勘确认书',
    create_by           BIGINT COMMENT '创建者',
    create_time         DATETIME DEFAULT NULL COMMENT '创建时间',
    update_by           BIGINT COMMENT '更新者',
    update_time         DATETIME DEFAULT NULL COMMENT '更新时间',
    remark              VARCHAR(500) DEFAULT '' COMMENT '备注'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='踏勘记录表';

-- 成交记录表
CREATE TABLE IF NOT EXISTS auction_deal (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    activity_id         BIGINT NOT NULL COMMENT '拍卖活动ID',
    item_id             BIGINT NOT NULL COMMENT '商品ID',
    user_id             BIGINT NOT NULL COMMENT '成交用户ID',
    real_name           VARCHAR(30) NOT NULL COMMENT '成交用户名称（真实姓名）',
    deal_price          DECIMAL(20,2) NOT NULL COMMENT '成交价格',
    deal_time           DATETIME NOT NULL COMMENT '成交时间',
    settlement_status   CHAR(1) DEFAULT '0' COMMENT '尾款结算状态(0未结算 1已结算 2违约)',
    breach_status       CHAR(1) DEFAULT '0' COMMENT '违约状态(0正常 1违约)',
    create_by           BIGINT COMMENT '创建者',
    create_time         DATETIME DEFAULT NULL COMMENT '创建时间',
    update_by           BIGINT COMMENT '更新者',
    update_time         DATETIME DEFAULT NULL COMMENT '更新时间',
    remark              VARCHAR(500) DEFAULT '' COMMENT '备注'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='成交记录表';

-- 尾款结算申请表
CREATE TABLE IF NOT EXISTS auction_settlement (
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    deal_id                 BIGINT NOT NULL COMMENT '成交记录ID',
    activity_id             BIGINT NOT NULL COMMENT '拍卖活动ID',
    user_id                 BIGINT NOT NULL COMMENT '用户ID',
    real_name               VARCHAR(30) NOT NULL COMMENT '成交用户名称（真实姓名）',
    settlement_size         DECIMAL(10,2) NOT NULL COMMENT '实际结算量',
    settlement_amount       DECIMAL(20,2) NOT NULL COMMENT '结算金额（尾款金额）',
    deposit_deduct_amount   DECIMAL(20,2) DEFAULT '0.00' COMMENT '保证金抵扣金额',
    actual_pay_amount       DECIMAL(20,2) NOT NULL COMMENT '实际支付金额',
    pay_type                CHAR(1) NOT NULL COMMENT '支付方式(1线上缴纳 2线下缴纳)',
    is_invoice              CHAR(1) DEFAULT '1' COMMENT '是否开票(0否 1是)',
    invoice_voucher         VARCHAR(255) DEFAULT '' COMMENT '开票凭证',
    pay_voucher             VARCHAR(255) DEFAULT '' COMMENT '线下支付凭证',
    pay_trade_no            VARCHAR(100) DEFAULT '' COMMENT '线上支付流水号',
    pay_time                DATETIME COMMENT '支付时间',
    audit_status            CHAR(1) DEFAULT '0' COMMENT '审批状态(0待审批 1通过 2拒绝)',
    audit_by                BIGINT COMMENT '审批人ID',
    audit_time              DATETIME COMMENT '审批时间',
    audit_remark            VARCHAR(500) DEFAULT '' COMMENT '审批备注',
    settlement_time         DATETIME COMMENT '结算完成时间',
    tax_price               DECIMAL(20,2) NOT NULL COMMENT '税价',
    create_by               BIGINT COMMENT '创建者',
    create_time             DATETIME DEFAULT NULL COMMENT '创建时间',
    update_by               BIGINT COMMENT '更新者',
    update_time             DATETIME DEFAULT NULL COMMENT '更新时间',
    remark                  VARCHAR(500) DEFAULT '' COMMENT '备注'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='尾款结算申请表';

-- 活动消息表
CREATE TABLE IF NOT EXISTS auction_message (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    activity_id     BIGINT NOT NULL COMMENT '拍卖活动ID',
    content         VARCHAR(255) NOT NULL COMMENT '内容',
    create_time     DATETIME  DEFAULT NULL COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='活动消息表';

-- 状态流转调度任务表
CREATE TABLE IF NOT EXISTS auction_state_schedule (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    activity_id     BIGINT NOT NULL COMMENT '活动ID',
    transition_type CHAR(1) NOT NULL COMMENT '流转类型：1-待启动→报名中 2-报名中→结束报名 3-结束报名→竞拍中 4-竞拍中→已结束',
    scheduled_time  DATETIME NOT NULL COMMENT '计划执行时间（支持毫秒精度）',
    status          CHAR(1) DEFAULT '0' COMMENT '任务状态：0-待执行 1-已执行 2-已取消',
    create_time     DATETIME DEFAULT NULL COMMENT '创建时间',
    update_time     DATETIME DEFAULT NULL COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='活动状态流转调度任务表';
