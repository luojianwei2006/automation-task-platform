-- ============================================
-- 自动化任务平台 · 数据库初始化脚本
-- 版本：v1.0 | 日期：2026-05-03
-- 共12张核心表
-- ============================================

-- 8.1 创建数据库
CREATE DATABASE IF NOT EXISTS `task_platform` 
  DEFAULT CHARACTER SET utf8mb4 
  COLLATE utf8mb4_general_ci;

USE `task_platform`;

-- 8.2 创建用户并授权（执行前请修改密码）
-- CREATE USER IF NOT EXISTS 'task_user'@'%' IDENTIFIED BY 'StrongPassword123!';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON task_platform.* TO 'task_user'@'%';
-- FLUSH PRIVILEGES;

-- ============================================
-- 二、用户相关表（3张）
-- ============================================

-- 2.1 用户表（t_user）
CREATE TABLE `t_user` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
  `phone` VARCHAR(20) NOT NULL COMMENT '手机号',
  `password` VARCHAR(128) NOT NULL COMMENT '密码（BCrypt加密）',
  `nickname` VARCHAR(32) COMMENT '昵称',
  `avatar_url` VARCHAR(512) COMMENT '头像URL',
  `real_name` VARCHAR(64) COMMENT '真实姓名（实名认证后）',
  `id_card` VARCHAR(128) COMMENT '身份证号（AES加密存储）',
  `real_auth_status` TINYINT DEFAULT 0 COMMENT '实名认证状态：0未认证 1审核中 2已认证 3失败',
  `wechat_account` VARCHAR(128) COMMENT '绑定微信账号',
  `alipay_account` VARCHAR(128) COMMENT '绑定支付宝账号',
  `invite_code` VARCHAR(16) UNIQUE COMMENT '用户邀请码（唯一）',
  `inviter_id` BIGINT COMMENT '邀请人ID',
  `device_fp` VARCHAR(128) COMMENT '设备指纹（Android ID+MAC+设备型号MD5）',
  `auto_mode` TINYINT DEFAULT 0 COMMENT '自动化模式：0手动 1半自动 2深度自动',
  `status` TINYINT DEFAULT 1 COMMENT '账号状态：0封禁 1正常',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY `uk_phone` (`phone`),
  UNIQUE KEY `uk_invite_code` (`invite_code`),
  KEY `idx_inviter_id` (`inviter_id`),
  KEY `idx_device_fp` (`device_fp`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 2.2 用户收益明细表（t_user_earnings）
CREATE TABLE `t_user_earnings` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '收益记录ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `related_id` BIGINT COMMENT '关联ID（任务记录ID/广告记录ID/邀请关系ID）',
  `type` TINYINT NOT NULL COMMENT '收益类型：1任务奖励 2广告奖励 3邀请返佣 4新手任务奖励',
  `amount` DECIMAL(10,2) NOT NULL COMMENT '收益金额',
  `balance_after` DECIMAL(10,2) COMMENT '变动后余额',
  `status` TINYINT DEFAULT 1 COMMENT '状态：0待审核 1已到账 2已撤销',
  `remark` VARCHAR(256) COMMENT '备注',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  KEY `idx_user_id` (`user_id`),
  KEY `idx_type` (`type`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收益明细表';

-- 2.3 邀请关系表（t_invite_relation）
CREATE TABLE `t_invite_relation` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '关系ID',
  `inviter_id` BIGINT NOT NULL COMMENT '邀请人ID',
  `invitee_id` BIGINT NOT NULL COMMENT '被邀请人ID',
  `invite_code` VARCHAR(16) NOT NULL COMMENT '邀请码',
  `status` TINYINT DEFAULT 0 COMMENT '状态：0进行中 1已完成（首月结束）',
  `commission_amount` DECIMAL(10,2) DEFAULT 0.00 COMMENT '累计返佣金额',
  `start_date` DATE COMMENT '返佣开始日期',
  `end_date` DATE COMMENT '返佣结束日期（首月）',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY `uk_invitee_id` (`invitee_id`),
  KEY `idx_inviter_id` (`inviter_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邀请关系表';

-- ============================================
-- 三、任务相关表（2张）
-- ============================================

-- 3.1 任务表（t_task）
CREATE TABLE `t_task` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '任务ID',
  `merchant_id` BIGINT NOT NULL COMMENT '发布商户ID',
  `title` VARCHAR(128) NOT NULL COMMENT '任务标题',
  `platform` TINYINT NOT NULL COMMENT '平台：1抖音 2小红书',
  `task_type` TINYINT NOT NULL COMMENT '任务类型：1点赞 2评论',
  `target_url` VARCHAR(1024) NOT NULL COMMENT '目标链接',
  `requirements` TEXT COMMENT '任务要求（文字说明）',
  `requirement_images` JSON COMMENT '任务要求图片（JSON数组）',
  `reward_amount` DECIMAL(10,2) NOT NULL COMMENT '单次奖励金额',
  `total_quota` INT NOT NULL COMMENT '总完成次数上限',
  `used_quota` INT DEFAULT 0 COMMENT '已使用配额',
  `daily_limit` INT DEFAULT 0 COMMENT '每日完成上限（0=不限）',
  `status` TINYINT DEFAULT 0 COMMENT '状态：0待审核 1已上架 2已暂停 3已结束 4已拒绝',
  `reject_reason` VARCHAR(256) COMMENT '拒绝原因',
  `budget_points` DECIMAL(12,2) NOT NULL COMMENT '预算点数（含15%服务费）',
  `used_points` DECIMAL(12,2) DEFAULT 0.00 COMMENT '已消耗点数',
  `deadline` DATETIME COMMENT '截止时间',
  `published_at` DATETIME COMMENT '上架时间',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY `idx_merchant_id` (`merchant_id`),
  KEY `idx_platform_task_type` (`platform`, `task_type`),
  KEY `idx_status` (`status`),
  KEY `idx_deadline` (`deadline`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务表';

-- 3.2 用户任务记录表（t_user_task_record）
CREATE TABLE `t_user_task_record` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `task_id` BIGINT NOT NULL COMMENT '任务ID',
  `screenshot_url` VARCHAR(512) COMMENT '截图URL',
  `submit_count` TINYINT DEFAULT 1 COMMENT '提交次数（最多2次）',
  `status` TINYINT DEFAULT 0 COMMENT '状态：0进行中 1待审核 2通过 3拒绝（拒绝后限1次重新提交回1）',
  `ai_check_result` VARCHAR(50) COMMENT 'AI审核结果（通过/疑似PS/截图模糊等）',
  `review_result` VARCHAR(256) COMMENT '人工审核结果/拒绝原因',
  `reward_amount` DECIMAL(10,2) COMMENT '奖励金额（审核通过后写入）',
  `auto_mode` TINYINT DEFAULT 0 COMMENT '执行模式：0手动 1半自动 2深度自动',
  `accepted_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '接取时间',
  `submitted_at` DATETIME COMMENT '提交时间',
  `checked_at` DATETIME COMMENT '审核时间',
  UNIQUE KEY `uk_user_task` (`user_id`, `task_id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_status` (`status`),
  KEY `idx_submitted_at` (`submitted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户任务记录表';

-- ============================================
-- 四、广告相关表（2张）
-- ============================================

-- 4.1 广告表（t_ad）
CREATE TABLE `t_ad` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '广告ID',
  `merchant_id` BIGINT NOT NULL COMMENT '发布商户ID',
  `title` VARCHAR(128) NOT NULL COMMENT '广告标题',
  `ad_type` TINYINT NOT NULL COMMENT '广告类型：1视频 2图文 3落地页',
  `material_url` VARCHAR(512) COMMENT '素材URL（视频/图片）',
  `landing_url` VARCHAR(1024) COMMENT '落地页链接',
  `reward_amount` DECIMAL(10,2) NOT NULL COMMENT '用户奖励金额',
  `budget_points` DECIMAL(12,2) NOT NULL COMMENT '预算点数',
  `used_points` DECIMAL(12,2) DEFAULT 0.00 COMMENT '已消耗点数',
  `completed_count` INT DEFAULT 0 COMMENT '已完成次数',
  `view_count` INT DEFAULT 0 COMMENT '曝光次数',
  `click_count` INT DEFAULT 0 COMMENT '点击次数',
  `status` TINYINT DEFAULT 0 COMMENT '状态：0待审 1投放中 2暂停 3结束',
  `reject_reason` VARCHAR(256) COMMENT '拒绝原因',
  `start_time` DATETIME COMMENT '投放开始时间',
  `end_time` DATETIME COMMENT '投放结束时间',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY `idx_merchant_id` (`merchant_id`),
  KEY `idx_ad_type` (`ad_type`),
  KEY `idx_status` (`status`),
  KEY `idx_start_end_time` (`start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='广告表';

-- 4.2 用户广告观看记录表（t_ad_record）
CREATE TABLE `t_ad_record` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `ad_id` BIGINT NOT NULL COMMENT '广告ID',
  `watch_start_time` DATETIME COMMENT '观看开始时间（服务端记录）',
  `watch_duration` INT COMMENT '观看时长（秒，服务端计算）',
  `reward_amount` DECIMAL(10,2) COMMENT '奖励金额',
  `status` TINYINT DEFAULT 0 COMMENT '状态：0观看中 1已完成 2已发奖',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `finished_at` DATETIME COMMENT '完成时间',
  UNIQUE KEY `uk_user_ad` (`user_id`, `ad_id`),
  KEY `idx_ad_id` (`ad_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户广告观看记录表';

-- ============================================
-- 五、商户相关表（1张）
-- ============================================

-- 5.1 商户表（t_merchant）
CREATE TABLE `t_merchant` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '商户ID',
  `name` VARCHAR(100) NOT NULL COMMENT '商户名称（企业名/个体工商户名）',
  `contact_name` VARCHAR(50) COMMENT '联系人姓名',
  `phone` VARCHAR(20) NOT NULL COMMENT '手机号',
  `password` VARCHAR(128) NOT NULL COMMENT '密码（BCrypt加密）',
  `license_no` VARCHAR(50) COMMENT '营业执照号',
  `license_img` VARCHAR(512) COMMENT '营业执照图片URL',
  `legal_person` VARCHAR(50) COMMENT '法人姓名',
  `legal_id_card` VARCHAR(128) COMMENT '法人身份证号（AES加密存储）',
  `auth_status` TINYINT DEFAULT 0 COMMENT '认证状态：0待审核 1通过 2拒绝',
  `reject_reason` VARCHAR(256) COMMENT '拒绝原因',
  `point_balance` DECIMAL(12,2) DEFAULT 0.00 COMMENT '点数余额',
  `total_recharge` DECIMAL(12,2) DEFAULT 0.00 COMMENT '累计充值金额',
  `total_consume` DECIMAL(12,2) DEFAULT 0.00 COMMENT '累计消费金额',
  `status` TINYINT DEFAULT 1 COMMENT '状态：0封禁 1正常',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY `uk_phone` (`phone`),
  KEY `idx_auth_status` (`auth_status`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户表';

-- ============================================
-- 六、财务相关表（2张）
-- ============================================

-- 6.1 充值记录表（t_recharge_record）
CREATE TABLE `t_recharge_record` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '充值记录ID',
  `merchant_id` BIGINT NOT NULL COMMENT '商户ID',
  `amount` DECIMAL(12,2) NOT NULL COMMENT '充值金额（元）',
  `points` DECIMAL(12,2) NOT NULL COMMENT '获得点数（含赠送）',
  `bonus_points` DECIMAL(12,2) DEFAULT 0.00 COMMENT '赠送点数',
  `pay_method` VARCHAR(20) COMMENT '支付方式：wechat alipay bank_transfer',
  `trade_no` VARCHAR(64) COMMENT '支付流水号',
  `status` TINYINT DEFAULT 0 COMMENT '状态：0待支付 1已支付 2已退款',
  `refund_amount` DECIMAL(12,2) COMMENT '退款金额',
  `refund_reason` VARCHAR(256) COMMENT '退款原因',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `paid_at` DATETIME COMMENT '支付时间',
  `refunded_at` DATETIME COMMENT '退款时间',
  KEY `idx_merchant_id` (`merchant_id`),
  KEY `idx_trade_no` (`trade_no`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='充值记录表';

-- 6.2 提现记录表（t_withdraw_record）
CREATE TABLE `t_withdraw_record` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '提现记录ID',
  `withdraw_no` VARCHAR(32) NOT NULL COMMENT '提现单号',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `amount` DECIMAL(10,2) NOT NULL COMMENT '提现金额',
  `method` VARCHAR(20) NOT NULL COMMENT '提现方式：wechat alipay',
  `account` VARCHAR(100) COMMENT '收款账号',
  `real_name` VARCHAR(50) COMMENT '收款人真实姓名',
  `status` TINYINT DEFAULT 0 COMMENT '状态：0待处理 1已打款 2已拒绝',
  `operator_id` BIGINT COMMENT '处理人ID',
  `reject_reason` VARCHAR(256) COMMENT '拒绝原因',
  `transaction_id` VARCHAR(64) COMMENT '打款交易ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `processed_at` DATETIME COMMENT '处理时间',
  UNIQUE KEY `uk_withdraw_no` (`withdraw_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提现记录表';

-- ============================================
-- 七、系统相关表（2张）
-- ============================================

-- 7.1 消息通知表（t_notification）
CREATE TABLE `t_notification` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '消息ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `type` TINYINT NOT NULL COMMENT '通知类型：1系统 2任务审核 3提现 4广告 5邀请',
  `title` VARCHAR(128) NOT NULL COMMENT '消息标题',
  `content` VARCHAR(512) NOT NULL COMMENT '消息内容',
  `target_id` BIGINT COMMENT '关联ID（任务ID/提现记录ID等）',
  `target_type` VARCHAR(32) COMMENT '关联类型（task/withdraw/invite等）',
  `status` TINYINT DEFAULT 0 COMMENT '状态：0未读 1已读',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `read_at` DATETIME COMMENT '阅读时间',
  KEY `idx_user_id` (`user_id`),
  KEY `idx_type` (`type`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息通知表';

-- 7.2 审核日志表（t_audit_log）
CREATE TABLE `t_audit_log` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日志ID',
  `audit_type` TINYINT NOT NULL COMMENT '审核类型：1任务审核 2提现审核 3商户认证审核 4广告审核',
  `target_id` BIGINT NOT NULL COMMENT '审核目标ID',
  `target_type` VARCHAR(32) NOT NULL COMMENT '审核目标类型（task/withdraw/merchant/ad）',
  `operator_id` BIGINT NOT NULL COMMENT '操作人ID',
  `operator_name` VARCHAR(64) NOT NULL COMMENT '操作人姓名',
  `action` TINYINT NOT NULL COMMENT '操作：1通过 2拒绝',
  `reason` VARCHAR(256) COMMENT '拒绝原因',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  KEY `idx_target_id_target_type` (`target_id`, `target_type`),
  KEY `idx_operator_id` (`operator_id`),
  KEY `idx_audit_type` (`audit_type`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核日志表';

-- ============================================
-- 八、管理员用户表（第3-6周新增）
-- ============================================

-- 8.0 管理员用户表（t_admin_user）
-- 统一承载：超管 + 商户管理员 + 商户操作员 + 财务
CREATE TABLE IF NOT EXISTS `t_admin_user` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '管理员ID',
  `username` VARCHAR(64) NOT NULL UNIQUE COMMENT '登录账号（手机号）',
  `password` VARCHAR(128) NOT NULL COMMENT '密码（BCrypt加密）',
  `display_name` VARCHAR(64) COMMENT '显示名称',
  `role_type` TINYINT NOT NULL COMMENT '角色：1超管 2商户管理员 3商户操作员 4财务',
  `merchant_id` BIGINT COMMENT '关联商户ID（商户角色时有效）',
  `status` TINYINT DEFAULT 1 COMMENT '状态：0禁用 1启用',
  `created_by` BIGINT COMMENT '创建人ID',
  `last_login_at` DATETIME COMMENT '最后登录时间',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY `idx_username` (`username`),
  KEY `idx_merchant_id` (`merchant_id`),
  KEY `idx_role_type` (`role_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员用户表';

-- ============================================
-- 九、初始化数据
-- ============================================

-- 9.1 初始化超级管理员账号（密码：Admin@2026，BCrypt加密后）
-- 账号：admin / 密码：Admin@2026
INSERT INTO `t_admin_user` (`username`, `password`, `display_name`, `role_type`, `status`)
VALUES ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJCZbi5/.dy', '超级管理员', 1, 1)
ON DUPLICATE KEY UPDATE `display_name` = '超级管理员';

-- 初始化示例用户（密码：123456）
INSERT INTO `t_user` (`phone`, `password`, `nickname`, `invite_code`, `status`)
VALUES ('13800001111', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJCZbi5/.dy', '测试用户', 'ABC12345', 1);

-- 初始化示例商户（密码：123456）
INSERT INTO `t_merchant` (`name`, `contact_name`, `phone`, `password`, `auth_status`, `point_balance`, `status`)
VALUES ('测试商户有限公司', '张三', '13800002222', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJCZbi5/.dy', 1, 10000.00, 1);

-- ============================================
-- 完成提示
-- ============================================
SELECT '数据库初始化完成！共创建12张表 + 初始化数据。' AS result;
