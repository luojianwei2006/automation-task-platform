-- ============================================================
-- 系统配置表
-- 存储全局配置项（域名、地址等），支持管理后台在线编辑
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
    config_value VARCHAR(500) COMMENT '配置值',
    description VARCHAR(255) COMMENT '说明',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- 初始数据（存在则更新值）
INSERT INTO sys_config (config_key, config_value, description) VALUES
('upload_domain', 'http://10.0.2.2:8085', '上传文件访问域名（Android模拟器使用）'),
('api_base_url', 'http://10.0.2.2:8085', 'API基础地址'),
('app_name', '自动化任务平台', '应用名称')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);
