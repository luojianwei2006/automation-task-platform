package com.task.platform.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 邀请关系表实体
 */
@Data
@TableName("t_invite_relation")
public class InviteRelation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 邀请人ID */
    private Long inviterId;

    /** 被邀请人ID */
    private Long inviteeId;

    /** 使用的邀请码 */
    private String inviteCode;

    /** 状态：0进行中 1已完成（首月过期后） */
    private Integer status = 0;

    /** 累计返佣金额 */
    private BigDecimal commissionAmount = BigDecimal.ZERO;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
