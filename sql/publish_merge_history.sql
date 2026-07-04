-- 清理旧表（如果存在）
DROP TABLE IF EXISTS `publish_merge_history`;

-- 视频合并历史记录表
CREATE TABLE IF NOT EXISTS `t_publish_merge_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `project_id` BIGINT NOT NULL COMMENT '项目ID',
    `video_ids` VARCHAR(500) DEFAULT NULL COMMENT '视频素材ID列表（逗号分隔，按合并顺序）',
    `music_id` BIGINT DEFAULT NULL COMMENT '背景音乐素材ID',
    `output_url` VARCHAR(500) DEFAULT NULL COMMENT '合并输出文件URL',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/COMPLETED/FAILED',
    `error_message` TEXT DEFAULT NULL COMMENT '失败原因',
    `duration_seconds` INT DEFAULT NULL COMMENT '视频时长（秒）',
    `file_size` BIGINT DEFAULT NULL COMMENT '文件大小（字节）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频合并历史记录';
