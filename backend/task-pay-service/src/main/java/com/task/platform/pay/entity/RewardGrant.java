package com.task.platform.pay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 任务奖励发放记录实体（pay-service 拥有）
 * 对应 t_reward_grant 表；task_record_id 为幂等键（唯一约束）。
 */
@Data
@TableName("t_reward_grant")
public class RewardGrant {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发放单号 RG+yyyymmddHHmmss+rand */
    @TableField("grant_no")
    private String grantNo;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 任务ID */
    @TableField("task_id")
    private Long taskId;

    /** 用户任务记录ID（幂等键） */
    @TableField("task_record_id")
    private Long taskRecordId;

    /** 奖励金额 */
    private BigDecimal amount;

    /** 状态：1已发放 2失败 */
    private Integer status;

    /** 业务幂等键（同 task_record_id） */
    @TableField("biz_id")
    private String bizId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("granted_at")
    private LocalDateTime grantedAt;
}
