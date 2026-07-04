-- 发布任务表增加奖励金额
ALTER TABLE `t_publish_task` ADD COLUMN `reward_amount` DECIMAL(10,2) DEFAULT NULL COMMENT '奖励金额' AFTER `status`;
