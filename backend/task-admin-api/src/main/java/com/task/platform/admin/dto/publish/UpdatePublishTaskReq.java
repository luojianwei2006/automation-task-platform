package com.task.platform.admin.dto.publish;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 更新发布任务请求
 */
@Data
public class UpdatePublishTaskReq {

    /** 发布平台：douyin/xiaohongshu/both */
    private String platforms;

    /** 发布文案 */
    private String publishText;

    /** 计划发布时间 */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime scheduledAt;

    /** 最大重试次数 */
    private Integer maxRetry;

    /** 内部备注 */
    private String remark;

    /** 奖励金额 */
    private java.math.BigDecimal rewardAmount;

    /** 图片URL列表（JSON数组字符串） */
    private String images;
}
