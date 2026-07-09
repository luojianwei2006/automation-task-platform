package com.task.platform.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商户流水记录
 * 对应数据库表：t_merchant_transaction
 */
@Data
@TableName("t_merchant_transaction")
public class MerchantTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商户ID */
    private Long merchantId;

    /** 类型：1=充值 2=任务扣费 3=退款 */
    private Integer type;

    /** 变动金额（正=增加，负=减少） */
    private BigDecimal amount;

    /** 变动前余额 */
    private BigDecimal balanceBefore;

    /** 变动后余额 */
    private BigDecimal balanceAfter;

    /** 关联业务ID */
    private Long relatedId;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
