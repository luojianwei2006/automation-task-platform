-- 商户流水记录表
-- 记录商户余额的每次变动：充值、任务扣费、退款等
CREATE TABLE IF NOT EXISTS t_merchant_transaction (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_id BIGINT NOT NULL COMMENT '商户ID',
  type TINYINT NOT NULL COMMENT '类型：1=充值 2=任务扣费 3=退款',
  amount DECIMAL(10,2) NOT NULL COMMENT '变动金额（正=增加，负=减少）',
  balance_before DECIMAL(10,2) NOT NULL COMMENT '变动前余额',
  balance_after DECIMAL(10,2) NOT NULL COMMENT '变动后余额',
  related_id BIGINT DEFAULT NULL COMMENT '关联业务ID',
  remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
  created_at DATETIME NOT NULL COMMENT '创建时间',
  INDEX idx_merchant_id (merchant_id),
  INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户流水记录表';
