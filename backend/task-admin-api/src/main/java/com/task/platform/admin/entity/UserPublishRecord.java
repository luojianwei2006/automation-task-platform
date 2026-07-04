package com.task.platform.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户发布任务领取/发布记录
 */
@Data
@TableName("t_user_publish_record")
public class UserPublishRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long taskId;

    /** CLAIMED / MERGED / SUBMITTED / PASSED / REJECTED */
    private String status;

    /** 截图URL（逗号分隔） */
    private String screenshots;

    /** 合并后的视频URL */
    private String mergedVideoUrl;

    /** 奖励金额 */
    @TableField("reward_amount")
    private java.math.BigDecimal rewardAmount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime claimedAt;

    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private String reviewResult;
}
