-- ============================================================
-- 后端 Stub 补全 + 端口冲突修复 —— DDL 补丁
-- 适用库：task_platform
-- 说明：本文件仅作新增/扩展，全部幂等（IF NOT EXISTS / 异常可忽略）。
--       pay-service 默认端口已改为 8087，网关路由同步；task-service 默认端口改为 8082。
-- ============================================================

-- (a) 任务奖励发放记录表（pay-service 拥有，对账 / 幂等双保险用）
CREATE TABLE IF NOT EXISTS t_reward_grant (
  id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  grant_no       VARCHAR(32)  NOT NULL                COMMENT '发放单号 RG+yyyymmddHHmmss+rand',
  user_id        BIGINT       NOT NULL                COMMENT '用户ID',
  task_id        BIGINT       NULL                    COMMENT '任务ID',
  task_record_id BIGINT       NOT NULL                COMMENT '用户任务记录ID（幂等键）',
  amount         DECIMAL(12,2) NOT NULL               COMMENT '奖励金额',
  status         TINYINT      NOT NULL DEFAULT 1      COMMENT '1已发放 2失败',
  biz_id         VARCHAR(64)  NULL                    COMMENT '业务幂等键（同 task_record_id）',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  granted_at     DATETIME     NULL,
  UNIQUE KEY uk_task_record (task_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务奖励发放记录表';

-- (b) t_user 实名审核扩展字段
-- 注意：若已执行过，重复执行会报 "Duplicate column" 错误，可忽略。
ALTER TABLE t_user
  ADD COLUMN hold_id_card_url      VARCHAR(512) NULL COMMENT '手持身份证照URL' AFTER id_card_back_url,
  ADD COLUMN real_auth_remark      VARCHAR(255) NULL COMMENT '实名审核备注/驳回原因' AFTER real_auth_status,
  ADD COLUMN real_auth_reviewed_by BIGINT       NULL COMMENT '审核人ID' AFTER real_auth_remark,
  ADD COLUMN real_auth_reviewed_at DATETIME     NULL COMMENT '审核时间' AFTER real_auth_reviewed_by;

-- (c) t_user_earnings 业务追溯键
ALTER TABLE t_user_earnings
  ADD COLUMN biz_id VARCHAR(64) NULL COMMENT '业务关联键（发放单号/提现单号）' AFTER remark;

-- (d) 提现无门槛：min_withdraw_amount 置 0（依赖 sys_config.config_key 唯一）
INSERT INTO sys_config (config_key, config_value, description, created_at, updated_at)
VALUES ('min_withdraw_amount', '0', '最低提现金额（0=无门槛）', NOW(), NOW())
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), updated_at = NOW();
