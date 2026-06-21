package com.task.platform.admin.dto.publish;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 素材列表视图对象
 */
@Data
public class MaterialListVO {

    private Long id;
    private Long projectId;
    private String projectName;
    private String type;
    private String title;
    private String fileUrl;
    private Long fileSize;
    private String content;
    private Integer duration;
    private String resolution;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
