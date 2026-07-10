-- ============================================================
-- 安卓 App 内版本更新 - 系统配置初始化补丁
-- ============================================================
-- 作用：向 sys_config 写入 App 更新所需的三个 KV 配置项。
--      运营后台可随时修改这些值来发布新版本 / 调整下载地址。
--      安卓端启动后拉取 GET /api/user/config 读取这三项进行版本比对。
--
-- 注意：
--   1. app_version 必须与安卓 build.gradle.kts 中的 versionName 使用同一套
--      语义化格式（如 1.0.0），否则字符串比较会失准。
--   2. app_download_url 请替换为「真实可访问」的 APK 地址（示例为占位）。
--      注意 Android 9+ 默认禁止明文 HTTP，若使用 http 需保证 apk 内置
--      android:usesCleartextTraffic="true"（本项目已开启）。
-- ============================================================

INSERT INTO sys_config (config_key, config_value, description) VALUES
('app_version', '1.0.0', '安卓App当前线上版本号（语义化版本，用于C端启动后比对更新）'),
('app_download_url', 'http://你的主机:8086/uploads/apk/app-release.apk', '安卓App最新APK下载地址（请替换为真实可访问地址）')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), description = VALUES(description);

-- app_name 已在 sys_config.sql 初始数据中（默认「自动化任务平台」），
-- 这里仅在尚不存在时插入，避免覆盖运营已配置的名称。
INSERT INTO sys_config (config_key, config_value, description)
SELECT 'app_name', '任务平台', '应用名称'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'app_name');
