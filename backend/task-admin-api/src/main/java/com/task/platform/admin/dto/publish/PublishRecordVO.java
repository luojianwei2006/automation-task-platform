package com.task.platform.admin.dto.publish;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发布记录返回VO（含用户手机号）
 */
@Data
public class PublishRecordVO {
    private Long id;
    private Long userId;
    private String userPhone;
    private Long taskId;
    private String taskName;
    private String status;
    private String screenshots;
    private String mergedVideoUrl;
    private BigDecimal rewardAmount;
    private LocalDateTime claimedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private String reviewResult;
}
