-- 给项目表加商户ID字段，实现商户数据隔离
ALTER TABLE t_project ADD COLUMN merchant_id BIGINT DEFAULT NULL COMMENT '所属商户ID' AFTER status;
CREATE INDEX idx_merchant_id ON t_project(merchant_id);
