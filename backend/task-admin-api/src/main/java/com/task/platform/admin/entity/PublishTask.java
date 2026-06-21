package com.task.platform.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 发布任务表实体（视频发布功能）
 * 对应数据库表：t_publish_task
 */
@Data
@TableName("t_publish_task")
public class PublishTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联项目ID */
    @TableField("project_id")
    private Long projectId;

    /** 发布平台：douyin/xiaohongshu/both */
    private String platforms;

    /** 发布文案 */
    @TableField("publish_text")
    private String publishText;

    /** 计划发布时间 */
    @TableField("scheduled_at")
    private LocalDateTime scheduledAt;

    /** 任务状态：pending/claimed/running/completed/failed/cancelled */
    private String status = "pending";

    /** 领取人ID */
    @TableField("claimed_by")
    private Long claimedBy;

    /** 领取时间 */
    @TableField("claimed_at")
    private LocalDateTime claimedAt;

    /** 完成时间 */
    @TableField("completed_at")
    private LocalDateTime completedAt;

    /** 失败原因 */
    @TableField("error_message")
    private String errorMessage;

    /** 重试次数 */
    @TableField("retry_count")
    private Integer retryCount = 0;

    /** 最大重试次数 */
    @TableField("max_retry")
    private Integer maxRetry = 3;

    /** 内部备注 */
    private String remark;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
