package com.task.platform.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 视频编辑任务
 */
@Data
@TableName("t_video_edit_task")
public class VideoEditTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联项目ID */
    private Long projectId;

    /** 编辑指令 JSON（EditInstruction） */
    private String instructionJson;

    /** 状态：PENDING/PROCESSING/COMPLETED/FAILED */
    private String status;

    /** 渲染完成后的视频访问URL */
    private String resultUrl;

    /** 视频时长（秒） */
    private Integer durationSeconds;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 失败原因 */
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
