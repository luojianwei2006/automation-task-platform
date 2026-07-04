package com.task.platform.admin.dto.publish;

import lombok.Data;

import java.util.List;

/**
 * 发布素材随机预览 VO（移动端任务详情用）
 *
 * 返回：文案x1、图片x1、音乐x1、视频（按 sortOrder 分组，每组x1）
 */
@Data
public class PublishMaterialPreviewVO {

    /** 随机文案（type=text，可能为 null） */
    private MaterialListVO textMaterial;

    /** 随机图片（type=image，可能为 null） */
    private MaterialListVO imageMaterial;

    /** 随机背景音乐（type=music，可能为 null） */
    private MaterialListVO musicMaterial;

    /** 视频素材分组：按 sortOrder 分组，每组随机返回一个视频 */
    private List<VideoGroupVO> videoGroups;
}
