-- ============================================================
-- 增量迁移：t_user_earnings 增加 biz_id 唯一索引（入账幂等键）
-- 执行前请先预检，确认无 NULL / 重复 biz_id 后再执行 ALTER：
--   SELECT COUNT(*) FROM t_user_earnings WHERE biz_id IS NULL;
--   SELECT biz_id, COUNT(*) c FROM t_user_earnings GROUP BY biz_id HAVING c > 1;
-- （现有数据 biz_id 为发放单号 RG...，与新值「纯数字 taskRecordId」不冲突；预检为空再执行）
-- ============================================================

ALTER TABLE t_user_earnings ADD UNIQUE KEY uk_biz_id (biz_id);
