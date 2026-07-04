package com.task.platform.admin.dto.publish;

import lombok.Data;

/**
 * 视频素材分组 VO（按 sortOrder 分组，每组随机返回一个视频）
 */
@Data
public class VideoGroupVO {

    /** 段落序号 */
    private Integer sortOrder;

    /** 本组随机选中的视频素材 */
    private MaterialListVO video;
}
