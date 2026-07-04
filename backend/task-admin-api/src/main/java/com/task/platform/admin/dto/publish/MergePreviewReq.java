package com.task.platform.admin.dto.publish;

import lombok.Data;
import java.util.List;

/**
 * 合并预览请求
 */
@Data
public class MergePreviewReq {
    /** 项目ID */
    private Long projectId;
    /** 背景音乐素材ID（为空则不加音乐） */
    private Long musicId;
    /** 待合并的视频素材ID列表（按此顺序合并，为空则查项目全部视频按 sortOrder 合并） */
    private List<Long> videoIds;

    // ==== 剪辑选项 ====
    /** 转场效果：none/fade/wipeleft/wiperight/slideleft/slideright（默认 none） */
    private String transition;
    /** 转场时长（秒，默认 0.5） */
    private Double transitionDuration;
    /** 是否渐入渐出 */
    private Boolean fadeInOut;
    /** 字幕文字（为空则不添加） */
    private String subtitle;
}
