package com.task.platform.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_withdraw_record")
public class WithdrawRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String withdrawNo;
    private Long userId;
    private BigDecimal amount;
    private String method;
    private String account;
    private String realName;
    private Integer status; // 0=待打款 1=已打款 2=已拒绝（本期无门槛提现，去掉"待审核"中间态）
    private String rejectReason;
    private String transactionId;
    private String transferVoucherUrl;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
}
