package com.task.platform.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 任务奖励发放记录实体（管理后台对账用）
 * 对应 t_reward_grant 表。
 */
@Data
@TableName("t_reward_grant")
public class RewardGrant {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("grant_no")
    private String grantNo;

    @TableField("user_id")
    private Long userId;

    @TableField("task_id")
    private Long taskId;

    @TableField("task_record_id")
    private Long taskRecordId;

    private BigDecimal amount;

    /** 1已发放 2失败 */
    private Integer status;

    @TableField("biz_id")
    private String bizId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("granted_at")
    private LocalDateTime grantedAt;
}
