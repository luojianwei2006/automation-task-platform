package com.task.platform.admin.dto.publish;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 发布任务视图对象
 */
@Data
public class PublishTaskVO {

    private Long id;
    private Long projectId;
    private String projectName;
    private String platforms;
    private String platform;
    private String publishText;
    private LocalDateTime scheduledAt;
    private String status;
    private Long claimedBy;
    private LocalDateTime claimedAt;
    private LocalDateTime completedAt;
    private LocalDateTime publishedAt;
    private String errorMessage;
    private Integer retryCount;
    private Integer maxRetry;
    private String remark;
    private BigDecimal rewardAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 任务图片URL列表（JSON数组） */
    private String images;

    /** 关联项目素材列表（任务详情时填充） */
    private List<MaterialListVO> materials;
}
