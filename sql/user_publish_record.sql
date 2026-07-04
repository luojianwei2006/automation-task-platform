-- 用户发布任务领取记录表
CREATE TABLE IF NOT EXISTS `t_user_publish_record` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `task_id` BIGINT NOT NULL COMMENT '发布任务ID',
    `status` VARCHAR(20) NOT NULL DEFAULT 'CLAIMED' COMMENT 'CLAIMED/MERGED/SUBMITTED/PASSED/REJECTED',
    `screenshots` VARCHAR(2048) DEFAULT NULL COMMENT '截图URL（逗号分隔，最多9张）',
    `merged_video_url` VARCHAR(500) DEFAULT NULL COMMENT '合并后的视频URL',
    `reward_amount` DECIMAL(10,2) DEFAULT NULL COMMENT '奖励金额（审核通过后写入）',
    `claimed_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
    `submitted_at` DATETIME COMMENT '提交审核时间',
    `reviewed_at` DATETIME COMMENT '审核时间',
    `review_result` VARCHAR(256) DEFAULT NULL COMMENT '审核结果/拒绝原因',
    UNIQUE KEY `uk_user_publish_task` (`user_id`, `task_id`),
    KEY `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户发布任务记录';
