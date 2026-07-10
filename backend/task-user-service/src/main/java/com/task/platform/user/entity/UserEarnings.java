package com.task.platform.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户收益明细表实体（C端用户服务用）
 * 对应数据库表：t_user_earnings
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

    /** 收益类型：1任务奖励 2广告奖励 3邀请返佣 4新手任务奖励 */
    private Integer type;

    /** 收益金额 */
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
