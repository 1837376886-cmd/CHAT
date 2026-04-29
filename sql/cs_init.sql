-- 在线客服功能初始化 SQL
-- 执行时间：2026-04-27

-- 1. sys_user 新增是否客服字段
ALTER TABLE sys_user ADD COLUMN is_customer_service tinyint(1) DEFAULT 0
  COMMENT '是否客服：0-否，1-是' AFTER status;

-- 2. 访客信息表
CREATE TABLE IF NOT EXISTS `chat_visitor` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `visitor_token` varchar(64) NOT NULL COMMENT '访客Token（前端持久化于localStorage）',
  `nickname` varchar(50) DEFAULT '访客' COMMENT '访客昵称',
  `ip` varchar(64) DEFAULT NULL COMMENT 'IP地址',
  `user_agent` varchar(500) DEFAULT NULL COMMENT '浏览器UA',
  `source_page` varchar(255) DEFAULT NULL COMMENT '来源页面URL',
  `bound_user_id` bigint DEFAULT NULL COMMENT '已绑定的sys_user.id（登录后回填，用于历史关联）',
  `last_cs_user_id` bigint DEFAULT NULL COMMENT '最近一次接待该访客的客服userId（用于优先分配）',
  `device_fingerprint` varchar(64) DEFAULT NULL COMMENT '设备指纹（用于区分同一IP下不同设备）',
  `last_session_end_time` datetime DEFAULT NULL COMMENT '最近一次会话结束时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_visitor_token` (`visitor_token`),
  KEY `idx_ip` (`ip`),
  KEY `idx_device_fingerprint` (`device_fingerprint`),
  KEY `idx_bound_user_id` (`bound_user_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='访客信息表';

-- 3. 客服会话表（独立表，不混用 chat_session）
CREATE TABLE IF NOT EXISTS `cs_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `visitor_id` bigint NOT NULL COMMENT '访客ID（chat_visitor.id）',
  `cs_user_id` bigint NOT NULL COMMENT '客服用户ID（sys_user.id）',
  `status` tinyint DEFAULT 1 COMMENT '状态：0-已结束，1-进行中，2-等待中（满员话术兜底）',
  `cs_unread_count` int DEFAULT 0 COMMENT '客服未读消息数（访客发送且客服离线时累加）',
  `start_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '会话开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '会话结束时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_visitor_id` (`visitor_id`),
  KEY `idx_cs_user_id` (`cs_user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服会话表';

-- 4. 客服消息表（独立表，不混用 chat_message）
CREATE TABLE IF NOT EXISTS `cs_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` bigint NOT NULL COMMENT '客服会话ID（cs_session.id）',
  `from_type` tinyint NOT NULL COMMENT '发送者类型：1-访客，2-客服，3-系统',
  `from_user_id` bigint DEFAULT NULL COMMENT '发送者用户ID（客服时有效）',
  `content` text NOT NULL COMMENT '消息内容',
  `msg_type` tinyint DEFAULT 1 COMMENT '消息类型：1-文本，2-图片',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服消息表';

-- 5. 客服配置表
CREATE TABLE IF NOT EXISTS `cs_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '客服用户ID',
  `max_sessions` int DEFAULT 5 COMMENT '最大同时接待数',
  `auto_reply` varchar(500) DEFAULT NULL COMMENT '自动回复语',
  `status` tinyint DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服配置表';

-- 6. 菜单配置（RuoYi Vue）
-- 客服管理目录
INSERT INTO `ruoyi-chat`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(2000, '聊天', 1, 10, 'chat', NULL, NULL, '', 1, 0, 'M', '0', '0', '', 'message', 'admin', '2026-04-25 15:45:22', 'admin', '2026-04-25 15:45:57', '');
INSERT INTO `ruoyi-chat`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(2003, '客服工作台', 2000, 1, 'workbench', 'cs/workbench/index', NULL, '', 1, 0, 'C', '0', '0', 'cs:workbench:list', 'message', 'admin', '2026-04-27 15:39:51', 'admin', '2026-04-27 15:52:06', '客服工作台');
INSERT INTO `ruoyi-chat`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(2004, '客服人员设置', 2000, 2, 'staff', 'cs/staff/index', NULL, '', 1, 0, 'C', '0', '0', 'cs:staff:list', 'peoples', 'admin', '2026-04-27 15:39:51', 'admin', '2026-04-27 15:52:13', '客服人员设置');
INSERT INTO `ruoyi-chat`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(2005, '我的客服历史', 2000, 3, 'myHistory', 'cs/myHistory/index', NULL, '', 1, 0, 'C', '0', '0', 'cs:myHistory:list', 'log', 'admin', '2026-04-27 15:39:51', 'admin', '2026-04-27 15:52:34', '我的客服历史');
-- 7. 访客标签表
CREATE TABLE IF NOT EXISTS `cs_visitor_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `visitor_id` bigint NOT NULL COMMENT '访客ID（chat_visitor.id）',
  `tag_name` varchar(50) NOT NULL COMMENT '标签名称',
  `create_by` bigint DEFAULT NULL COMMENT '创建人（客服userId）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_visitor_id` (`visitor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='访客标签表';
