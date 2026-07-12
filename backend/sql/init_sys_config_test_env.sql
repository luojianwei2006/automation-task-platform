-- 初始化测试环境开关（test_env）
-- 幂等：仅当 sys_config 表中尚不存在 config_key='test_env' 时才插入。
-- 用户也可通过管理后台 /settings 页面切换开关，admin-api 会按需 upsert。

INSERT INTO sys_config (config_key, config_value, description)
SELECT 'test_env', 'false', '测试环境模式（开启后短信验证码统一为666666）'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'test_env');

INSERT INTO sys_config (config_key, config_value, description)
SELECT 'require_phone_verify', 'true', '注册时是否验证手机号（短信验证码），true=强制验证，false=免验证'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'require_phone_verify');
