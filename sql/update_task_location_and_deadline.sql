-- 任务平台数据库增量更新脚本
-- 功能：为任务表和用户任务记录表增加字段（定位、截止时间、审核时间线）
-- 日期：2026-05-17

-- ========================================
-- 1. 修改 t_task 表：增加定位相关字段和提交截止时长
-- ========================================

ALTER TABLE t_task 
ADD COLUMN require_location TINYINT(1) DEFAULT 0 COMMENT '是否需要定位验证：0否 1是',
ADD COLUMN location_lat DECIMAL(10, 8) DEFAULT NULL COMMENT '任务位置纬度',
ADD COLUMN location_lng DECIMAL(11, 8) DEFAULT NULL COMMENT '任务位置经度',
ADD COLUMN location_desc VARCHAR(255) DEFAULT NULL COMMENT '位置描述（如店名）',
ADD COLUMN submit_deadline_hours INT DEFAULT 24 COMMENT '提交截止时长（小时，从接取时算起）';

-- ========================================
-- 2. 修改 t_user_task_record 表：增加审核时间线字段
-- ========================================

ALTER TABLE t_user_task_record
ADD COLUMN accept_deadline DATETIME DEFAULT NULL COMMENT '提交截止时间（接取后 N 小时内必须提交）',
ADD COLUMN ai_checked_at DATETIME DEFAULT NULL COMMENT 'AI审核时间（预留）',
ADD COLUMN manual_checked_at DATETIME DEFAULT NULL COMMENT '人工审核时间',
ADD COLUMN reward_granted_at DATETIME DEFAULT NULL COMMENT '奖励发放时间';
