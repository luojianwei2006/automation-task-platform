package com.task.platform.admin.dto.publish;

import lombok.Data;

/**
 * 视频编辑提交请求
 */
@Data
public class VideoEditReq {
    /** 项目ID（可选，用于关联） */
    private Long projectId;
    /** 编辑指令 */
    private VideoEditInstruction instruction;
}
