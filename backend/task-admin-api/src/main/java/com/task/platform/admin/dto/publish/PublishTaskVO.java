package com.task.platform.admin.dto.publish;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发布任务视图对象
 */
@Data
public class PublishTaskVO {

    private Long id;
    private Long projectId;
    private String platforms;
    private String publishText;
    private LocalDateTime scheduledAt;
    private String status;
    private Long claimedBy;
    private LocalDateTime claimedAt;
    private LocalDateTime completedAt;
    private String errorMessage;
    private Integer retryCount;
    private Integer maxRetry;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 关联项目素材列表（任务详情时填充） */
    private List<MaterialListVO> materials;
}
