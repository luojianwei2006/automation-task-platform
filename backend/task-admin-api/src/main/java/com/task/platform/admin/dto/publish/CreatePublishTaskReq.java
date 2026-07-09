package com.task.platform.admin.dto.publish;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建发布任务请求
 */
@Data
public class CreatePublishTaskReq {

    /** 关联项目ID（必填） */
    private Long projectId;

    /** 发布平台：douyin/xiaohongshu/both（必填） */
    private String platforms;

    /** 发布文案 */
    private String publishText;

    /** 过期时间 */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime scheduledAt;

    /** 最大重试次数（默认3） */
    private Integer maxRetry;

    /** 内部备注 */
    private String remark;

    /** 图片URL列表（JSON数组字符串），前端上传后传入 */
    private String images;

    /** 奖励金额（单次奖励） */
    private java.math.BigDecimal rewardAmount;

    /** 总配额（可领取/完成的总次数，≥1，默认1） */
    private Integer totalQuota;
}
