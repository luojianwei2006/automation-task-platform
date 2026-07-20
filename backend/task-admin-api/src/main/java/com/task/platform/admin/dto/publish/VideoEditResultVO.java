package com.task.platform.admin.dto.publish;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 视频编辑提交结果（异步：返回任务ID，客户端轮询结果）
 */
@Data
@AllArgsConstructor
public class VideoEditResultVO {
    /** 编辑任务ID（用于轮询） */
    private Long taskId;
    /** 渲染完成后的视频访问URL（完成时回填） */
    private String resultUrl;
    /** 视频时长（秒） */
    private Integer durationSeconds;
    /** 文件大小（字节） */
    private Long fileSize;
}
