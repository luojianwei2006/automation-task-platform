-- ============================================================
-- 增量迁移：删除 t_reward_grant 表（原 pay-service 自动发奖记录）
-- 任务审核奖励已改为经 task-user-service 内部接口入账到 t_user_earnings，
-- t_reward_grant 不再写入，可安全删除。
--
-- 建议先备份再删除：
--   CREATE TABLE t_reward_grant_bak LIKE t_reward_grant;
--   INSERT INTO t_reward_grant_bak SELECT * FROM t_reward_grant;
-- ============================================================

DROP TABLE IF EXISTS t_reward_grant;
