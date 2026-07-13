-- 协议文档表（替代将大段 HTML 塞入 sys_config）
-- 与后端 t_agreement 实体、AgreementMapper 一一对应。
CREATE TABLE IF NOT EXISTS t_agreement (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  type         VARCHAR(20)  NOT NULL,
  title        VARCHAR(200) DEFAULT '',
  content_html LONGTEXT,
  version      INT          NOT NULL DEFAULT 1,
  updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  updated_by   VARCHAR(50)  DEFAULT '',
  PRIMARY KEY (id),
  UNIQUE KEY uk_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 初始数据（幂等：已存在则跳过，不覆盖运营已编辑内容）
INSERT IGNORE INTO t_agreement(type, title) VALUES
('about',   '关于我们'),
('privacy', '隐私协议'),
('register','注册协议');
