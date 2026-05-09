package com.task.platform.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户任务记录表实体
 * 对应数据库表：t_user_task_record
 */
@Data
@TableName("t_user_task_record")
public class UserTaskRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 任务ID */
    @TableField("task_id")
    private Long taskId;

    /** 截图URL */
    @TableField("screenshot_url")
    private String screenshotUrl;

    /** 提交次数（最多2次） */
    @TableField("submit_count")
    private Integer submitCount;

    /** 状态：0进行中 1待审核 2通过 3拒绝 */
    private Integer status;

    /** AI审核结果 */
    @TableField("ai_check_result")
    private String aiCheckResult;

    /** 人工审核结果/拒绝原因 */
    @TableField("review_result")
    private String reviewResult;

    /** 奖励金额（审核通过后写入） */
    @TableField("reward_amount")
    private BigDecimal rewardAmount;

    /** 执行模式：0手动 1半自动 2深度自动 */
    @TableField("auto_mode")
    private Integer autoMode;

    /** 接取时间 */
    @TableField("accepted_at")
    private LocalDateTime acceptedAt;

    /** 提交时间 */
    @TableField("submitted_at")
    private LocalDateTime submittedAt;

    /** 审核时间 */
    @TableField("checked_at")
    private LocalDateTime checkedAt;
}
