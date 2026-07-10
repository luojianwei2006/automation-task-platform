package com.task.platform.pay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户收益明细实体（pay-service 镜像，写奖励发放流水用）
 * 对应 t_user_earnings 表。余额 = status=1 的最新一条 balance_after。
 */
@Data
@TableName("t_user_earnings")
public class UserEarnings {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("related_id")
    private Long relatedId;

    /** 收益类型：1任务奖励 2广告奖励 3邀请返佣 4新手任务 5提现 */
    private Integer type;

    /** 收益金额（正负表示增减） */
    private BigDecimal amount;

    /** 变动后余额 */
    @TableField("balance_after")
    private BigDecimal balanceAfter;

    /** 状态：0待审核 1已到账 2已撤销 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 业务关联键（发放单号/提现单号） */
    @TableField("biz_id")
    private String bizId;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
