package com.task.platform.admin.controller;

import com.task.platform.admin.dto.publish.PublishMaterialPreviewVO;
import com.task.platform.admin.service.PublishMaterialService;
import com.task.platform.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 移动端素材预览接口
 *
 * @author TaskPlatform
 */
@RestController
@RequestMapping("/mobile/publish/materials")
@RequiredArgsConstructor
public class MobilePublishMaterialController {

    private final PublishMaterialService publishMaterialService;

    /**
     * 获取项目随机素材（文案x1、图片x1、音乐x1、视频按 sortOrder 分组每组x1）
     * <p>
     * 限频：同一用户+同一项目 60 秒内返回缓存，超出后重新随机
     */
    @GetMapping
    public ApiResponse<PublishMaterialPreviewVO> getMaterials(
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "0") Long userId,
            @RequestParam Long projectId) {

        if (projectId == null || projectId <= 0) {
            return ApiResponse.error(400, "projectId 不能为空");
        }

        PublishMaterialPreviewVO vo = publishMaterialService.getRandomPreview(projectId, userId);
        return ApiResponse.success(vo);
    }
}
