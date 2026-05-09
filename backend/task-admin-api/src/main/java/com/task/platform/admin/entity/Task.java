package com.task.platform.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 任务表实体（管理后台用）
 * 对应数据库表：t_task
 */
@Data
@TableName("t_task")
public class Task {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发布商户ID */
    private Long merchantId;

    /** 任务标题 */
    private String title;

    /** 平台：1抖音 2小红书 */
    private Integer platform;

    /** 任务类型：1点赞 2评论 */
    private Integer taskType;

    /** 目标链接 */
    @TableField("target_url")
    private String targetUrl;

    /** 任务要求（文字说明） */
    private String requirements;

    /** 任务要求图片（JSON数组） */
    @TableField("requirement_images")
    private String requirementImages;

    /** 单次奖励金额 */
    @TableField("reward_amount")
    private BigDecimal rewardAmount;

    /** 总完成次数上限 */
    @TableField("total_quota")
    private Integer totalQuota;

    /** 已使用配额 */
    @TableField("used_quota")
    private Integer usedQuota;

    /** 每日完成上限（0=不限） */
    @TableField("daily_limit")
    private Integer dailyLimit;

    /** 状态：0待审核 1已上架 2已暂停 3已结束 4已拒绝 */
    private Integer status;

    /** 拒绝原因 */
    @TableField("reject_reason")
    private String rejectReason;

    /** 预算点数（含15%服务费） */
    @TableField("budget_points")
    private BigDecimal budgetPoints;

    /** 已消耗点数 */
    @TableField("used_points")
    private BigDecimal usedPoints;

    /** 截止时间 */
    private LocalDateTime deadline;

    /** 上架时间 */
    @TableField("published_at")
    private LocalDateTime publishedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
