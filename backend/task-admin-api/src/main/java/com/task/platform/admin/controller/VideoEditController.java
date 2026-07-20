package com.task.platform.admin.controller;

import com.task.platform.admin.dto.publish.VideoEditReq;
import com.task.platform.admin.dto.publish.VideoEditResultVO;
import com.task.platform.admin.entity.VideoEditTask;
import com.task.platform.admin.service.VideoEditService;
import com.task.platform.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 视频编辑接口
 * 提交编辑指令（异步渲染），客户端轮询任务状态拿到结果URL。
 * 结果URL 可直接作为发布素材走现有抖音/微信视频号发布流程。
 */
@RestController
@RequestMapping("/mobile/publish/video-edit")
@RequiredArgsConstructor
public class VideoEditController {

    private final VideoEditService videoEditService;

    /** 提交编辑任务 */
    @PostMapping
    public ApiResponse<VideoEditResultVO> edit(@RequestBody VideoEditReq req) {
        try {
            VideoEditResultVO result = videoEditService.submit(req);
            return ApiResponse.success(result, "已提交编辑任务");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "编辑提交失败: " + e.getMessage());
        }
    }

    /** 查询编辑任务结果（轮询） */
    @GetMapping("/{taskId}")
    public ApiResponse<VideoEditTask> getTask(@PathVariable Long taskId) {
        VideoEditTask task = videoEditService.getTask(taskId);
        if (task == null) return ApiResponse.error(404, "任务不存在");
        return ApiResponse.success(task);
    }
}
