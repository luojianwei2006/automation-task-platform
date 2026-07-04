-- 合并历史增加状态字段
ALTER TABLE `t_publish_merge_history` 
    ADD COLUMN `status` VARCHAR(20) NOT NULL DEFAULT 'COMPLETED' COMMENT 'PENDING/PROCESSING/COMPLETED/FAILED' AFTER `output_url`,
    ADD COLUMN `error_message` TEXT DEFAULT NULL COMMENT '失败原因' AFTER `status`;
