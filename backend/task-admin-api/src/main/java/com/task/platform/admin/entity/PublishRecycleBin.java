package com.task.platform.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 回收站表实体（视频发布功能）
 * 对应数据库表：t_recycle_bin
 */
@Data
@TableName("t_recycle_bin")
public class PublishRecycleBin {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 原始表名 */
    @TableField("original_table")
    private String originalTable;

    /** 原始记录ID */
    @TableField("original_id")
    private Long originalId;

    /** 删除时数据快照(JSON) */
    @TableField("data_json")
    private String dataJson;

    /** 删除人ID */
    @TableField("deleted_by")
    private Long deletedBy;

    /** 删除时间 */
    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    /** 是否已恢复：0=未恢复 1=已恢复 */
    private Integer restored = 0;

    /** 过期自动清理时间 */
    @TableField("expired_at")
    private LocalDateTime expiredAt;
}
