package com.task.platform.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 视频合并历史记录
 */
@Data
@TableName("t_publish_merge_history")
public class PublishMergeHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 视频素材ID列表（逗号分隔，按合并顺序） */
    private String videoIds;

    /** 背景音乐素材ID */
    private Long musicId;

    /** 合并输出文件URL */
    private String outputUrl;

    /** 状态：PENDING/PROCESSING/COMPLETED/FAILED */
    private String status;

    /** 失败原因 */
    private String errorMessage;

    /** 视频时长（秒） */
    private Integer durationSeconds;

    /** 文件大小（字节） */
    private Long fileSize;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
