package com.task.platform.admin.dto.publish;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 回收站视图对象
 */
@Data
public class RecycleBinVO {

    private Long id;
    private String originalTable;
    private Long originalId;
    private String dataJson;
    private Long deletedBy;
    private LocalDateTime deletedAt;
    private Integer restored;
    private LocalDateTime expiredAt;
}
