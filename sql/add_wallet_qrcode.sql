-- 用户表增加收款码字段
ALTER TABLE `t_user` 
    ADD COLUMN `wechat_qrcode` VARCHAR(512) NULL COMMENT '微信收款码URL' AFTER `alipay_account`,
    ADD COLUMN `alipay_qrcode` VARCHAR(512) NULL COMMENT '支付宝收款码URL' AFTER `wechat_qrcode`;
