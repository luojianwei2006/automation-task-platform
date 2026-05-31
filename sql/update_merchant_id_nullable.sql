-- ============================================
-- 迁移脚本：允许 t_task.merchant_id 为 NULL
-- 执行日期：2026-05-24
-- 注意：MODIFY COLUMN 必须显式写 NULL，否则默认变成 NOT NULL
-- ============================================

-- 1. 修改 t_task 表：merchant_id 允许 NULL（显式指定 NULL 关键字）
ALTER TABLE `t_task` 
  MODIFY COLUMN `merchant_id` BIGINT NULL COMMENT '发布商户ID（NULL=平台发布）';

-- 2. 验证修改结果（应显示 IS_NULLABLE = YES）
-- SELECT COLUMN_NAME, IS_NULLABLE, COLUMN_COMMENT 
-- FROM INFORMATION_SCHEMA.COLUMNS 
-- WHERE TABLE_SCHEMA = 'task_platform' AND TABLE_NAME = 't_task' AND COLUMN_NAME = 'merchant_id';
