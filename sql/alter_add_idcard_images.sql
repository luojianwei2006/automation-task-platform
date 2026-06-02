-- 身份证照片URL字段补齐
-- 为 t_user 表新增身份证正面照URL和背面照URL两个字段

ALTER TABLE t_user ADD COLUMN id_card_front_url VARCHAR(512) COMMENT '身份证正面照URL' AFTER id_card;

ALTER TABLE t_user ADD COLUMN id_card_back_url VARCHAR(512) COMMENT '身份证背面照URL' AFTER id_card_front_url;
