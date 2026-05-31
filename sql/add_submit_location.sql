-- 为 t_user_task_record 表添加提交定位字段
ALTER TABLE `t_user_task_record`
    ADD COLUMN `submit_lat` DOUBLE COMMENT '提交时纬度',
    ADD COLUMN `submit_lng` DOUBLE COMMENT '提交时经度';
