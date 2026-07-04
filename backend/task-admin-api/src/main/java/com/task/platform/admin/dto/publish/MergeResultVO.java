package com.task.platform.admin.dto.publish;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 合并预览结果
 */
@Data
@AllArgsConstructor
public class MergeResultVO {
    /** 合并后文件访问URL */
    private String url;
    /** 视频时长（秒） */
    private Integer durationSeconds;
    /** 文件大小（字节） */
    private Long fileSize;
    /** 历史记录ID */
    private Long historyId;
}
