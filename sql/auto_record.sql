-- 自动化操作记录表
-- 记录每次自动化执行的步骤详情

CREATE TABLE IF NOT EXISTS t_auto_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    task_id BIGINT NOT NULL COMMENT '任务ID',
    step VARCHAR(50) COMMENT '步骤: open_app/search/play_video/like/comment/screenshot',
    action VARCHAR(100) COMMENT '具体操作描述',
    status TINYINT DEFAULT 0 COMMENT '0执行中 1成功 2失败',
    result VARCHAR(256) COMMENT '执行结果',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_user_task (user_id, task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自动化操作记录表';
