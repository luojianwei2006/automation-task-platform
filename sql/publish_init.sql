-- ============================================================
-- 视频发布功能 - 数据库初始化脚本
-- ============================================================

-- 项目表
CREATE TABLE t_project (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    name          VARCHAR(200)  NOT NULL COMMENT '项目名称',
    description   TEXT          COMMENT '项目描述',
    cover_url     VARCHAR(500)  COMMENT '封面图URL',
    status        TINYINT       DEFAULT 1 COMMENT '1=正常 0=已删除',
    created_at    DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目表';

-- 素材表
CREATE TABLE t_material (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id    BIGINT        NOT NULL COMMENT '关联项目ID',
    type          VARCHAR(20)   NOT NULL COMMENT 'text/image/music/video',
    title         VARCHAR(300)  COMMENT '素材标题',
    file_url      VARCHAR(500)  NOT NULL COMMENT '文件URL',
    file_size     BIGINT        COMMENT '文件大小(字节)',
    content       TEXT          COMMENT '文案内容(type=text时)',
    duration      INT           COMMENT '时长(秒)',
    resolution    VARCHAR(50)   COMMENT '分辨率',
    sort_order    INT           DEFAULT 0 COMMENT '段落序号',
    deleted       TINYINT       DEFAULT 0 COMMENT '逻辑删除: 0=正常 1=已删除',
    created_at    DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_project_id (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='素材表';

-- 回收站表
CREATE TABLE t_recycle_bin (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    original_table  VARCHAR(50)   NOT NULL COMMENT '原始表名',
    original_id     BIGINT        NOT NULL COMMENT '原始记录ID',
    data_json       TEXT          NOT NULL COMMENT '删除时数据快照(JSON)',
    deleted_by      BIGINT        COMMENT '删除人ID',
    deleted_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    restored        TINYINT       DEFAULT 0 COMMENT '0=未恢复 1=已恢复',
    expired_at      DATETIME      COMMENT '过期自动清理时间',
    INDEX idx_original (original_table, original_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回收站表';

-- 发布任务表
CREATE TABLE t_publish_task (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id      BIGINT        NOT NULL COMMENT '关联项目ID',
    platforms       VARCHAR(50)   NOT NULL COMMENT 'douyin/xiaohongshu/both',
    publish_text    TEXT          COMMENT '发布文案',
    scheduled_at    DATETIME      COMMENT '计划发布时间',
    status          VARCHAR(20)   DEFAULT 'pending' COMMENT 'pending/claimed/running/completed/failed/cancelled',
    claimed_by      BIGINT        COMMENT '领取人ID',
    claimed_at      DATETIME      COMMENT '领取时间',
    completed_at    DATETIME      COMMENT '完成时间',
    error_message   TEXT          COMMENT '失败原因',
    retry_count     INT           DEFAULT 0,
    max_retry       INT           DEFAULT 3,
    remark          TEXT          COMMENT '内部备注',
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_project_id (project_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发布任务表';
