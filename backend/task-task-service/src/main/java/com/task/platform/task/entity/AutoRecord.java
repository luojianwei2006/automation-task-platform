package com.task.platform.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 自动化操作记录表实体
 * 对应数据库表：t_auto_record
 */
@Data
@TableName("t_auto_record")
public class AutoRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 任务ID */
    @TableField("task_id")
    private Long taskId;

    /** 步骤标识: open_app/search/play_video/like/comment/screenshot */
    private String step;

    /** 具体操作描述 */
    private String action;

    /** 状态：0执行中 1成功 2失败 */
    private Integer status;

    /** 执行结果 */
    private String result;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
