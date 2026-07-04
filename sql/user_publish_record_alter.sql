-- 增加奖励和审核字段
ALTER TABLE `t_user_publish_record`
    ADD COLUMN `reward_amount` DECIMAL(10,2) DEFAULT NULL COMMENT '奖励金额' AFTER `merged_video_url`,
    ADD COLUMN `reviewed_at` DATETIME DEFAULT NULL COMMENT '审核时间' AFTER `submitted_at`;
