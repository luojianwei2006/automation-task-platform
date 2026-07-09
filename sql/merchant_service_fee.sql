-- 商户表新增服务费率字段（2026-07-07）
ALTER TABLE t_merchant
ADD COLUMN service_fee_rate DECIMAL(5,4) NOT NULL DEFAULT 0.1500 COMMENT '服务费率（如 0.1500 = 15%）';
