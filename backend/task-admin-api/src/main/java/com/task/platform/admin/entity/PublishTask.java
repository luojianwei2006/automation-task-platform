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

    /** 任务状态：pending/online/rejected/offline/claimed/running/completed/failed/cancelled */
    private String status = "pending";

    /** 奖励金额（单次奖励） */
    @TableField("reward_amount")
    private java.math.BigDecimal rewardAmount;

    /** 总配额（可领取/完成的总次数），命名对齐普通任务 t_task */
    @TableField("total_quota")
    private Integer totalQuota = 1;

    /** 已用配额（已成功结算次数） */
    @TableField("used_quota")
    private Integer usedQuota = 0;

    /** 预算点数（含服务费，只读展示），= 单笔含费成本 × 总配额 */
    @TableField("budget_points")
    private java.math.BigDecimal budgetPoints = java.math.BigDecimal.ZERO;

    /** 已消耗点数（已结算累计含费成本） */
    @TableField("used_points")
    private java.math.BigDecimal usedPoints = java.math.BigDecimal.ZERO;

    /** 领取人ID */
    @TableField("claimed_by")
    private Long claimedBy;

    /** 领取时间 */
    @TableField("claimed_at")
    private LocalDateTime claimedAt;

    /** 完成时间 */
    @TableField("completed_at")
    private LocalDateTime completedAt;

    /** 上架时间（审核通过时设置） */
    @TableField("published_at")
    private LocalDateTime publishedAt;

    /** 失败原因 / 拒绝原因 */
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

    /** 任务图片URL列表（JSON数组） */
    private String images;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
