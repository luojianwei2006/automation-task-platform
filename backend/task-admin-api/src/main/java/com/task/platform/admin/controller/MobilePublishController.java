package com.task.platform.admin.controller;

import com.task.platform.admin.dto.publish.ClaimReq;
import com.task.platform.admin.dto.publish.CompleteReq;
import com.task.platform.admin.dto.publish.MaterialListVO;
import com.task.platform.admin.dto.publish.PublishTaskVO;
import com.task.platform.admin.entity.PublishMaterial;
import com.task.platform.admin.entity.PublishTask;
import com.task.platform.admin.service.MobilePublishService;
import com.task.platform.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 移动端发布接口（视频发布功能）
 *
 * @author TaskPlatform
 */
@RestController
@RequestMapping("/mobile/publish/tasks")
@RequiredArgsConstructor
public class MobilePublishController {

    private final MobilePublishService mobilePublishService;

    /**
     * 可领取任务列表（pending + 当前用户已claimed）
     * GET /api/mobile/publish/tasks?userId=
     */
    @GetMapping
    public ApiResponse<List<PublishTaskVO>> getAvailableTasks(@RequestParam Long userId) {
        List<PublishTask> tasks = mobilePublishService.getAvailableTasks(userId);
        List<PublishTaskVO> vos = tasks.stream().map(this::toVO).collect(Collectors.toList());
        return ApiResponse.success(vos);
    }

    /**
     * 领取任务
     * POST /api/mobile/publish/tasks/{id}/claim
     */
    @PostMapping("/{id}/claim")
    public ApiResponse<Map<String, Object>> claim(@PathVariable Long id,
                                                   @RequestBody ClaimReq req) {
        if (req.getUserId() == null) {
            return ApiResponse.error(400, "userId 不能为空");
        }

        try {
            PublishTask task = mobilePublishService.claim(id, req.getUserId());
            Map<String, Object> data = new HashMap<>();
            data.put("taskId", task.getId());
            data.put("status", task.getStatus());
            data.put("claimedAt", task.getClaimedAt());
            return ApiResponse.success(data, "任务领取成功");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 我的任务（已领取/执行中）
     * GET /api/mobile/publish/tasks/my?userId=
     */
    @GetMapping("/my")
    public ApiResponse<List<PublishTaskVO>> getMyTasks(@RequestParam Long userId) {
        List<PublishTask> tasks = mobilePublishService.getMyTasks(userId);
        List<PublishTaskVO> vos = tasks.stream().map(this::toVO).collect(Collectors.toList());
        return ApiResponse.success(vos);
    }

    /**
     * 完成任务上报
     * POST /api/mobile/publish/tasks/{id}/complete
     */
    @PostMapping("/{id}/complete")
    public ApiResponse<Map<String, Object>> complete(@PathVariable Long id,
                                                      @RequestBody CompleteReq req) {
        if (req.getUserId() == null) {
            return ApiResponse.error(400, "userId 不能为空");
        }

        try {
            PublishTask task = mobilePublishService.complete(id, req.getUserId(), req.getResultMessage());
            Map<String, Object> data = new HashMap<>();
            data.put("taskId", task.getId());
            data.put("status", task.getStatus());
            data.put("completedAt", task.getCompletedAt());
            return ApiResponse.success(data, "任务完成上报成功");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 任务详情（移动端，含素材）
     * GET /api/mobile/publish/tasks/{id}/detail
     */
    @GetMapping("/{id}/detail")
    public ApiResponse<PublishTaskVO> getDetail(@PathVariable Long id) {
        PublishTask task = mobilePublishService.getTaskById(id);
        if (task == null) {
            return ApiResponse.error(404, "任务不存在");
        }
        PublishTaskVO vo = toVO(task);

        List<PublishMaterial> materials = mobilePublishService.getTaskMaterials(id);
        vo.setMaterials(materials.stream().map(m -> {
            MaterialListVO mvo = new MaterialListVO();
            mvo.setId(m.getId());
            mvo.setProjectId(m.getProjectId());
            mvo.setType(m.getType());
            mvo.setTitle(m.getTitle());
            mvo.setFileUrl(m.getFileUrl());
            mvo.setFileSize(m.getFileSize());
            mvo.setContent(m.getContent());
            mvo.setDuration(m.getDuration());
            mvo.setResolution(m.getResolution());
            mvo.setSortOrder(m.getSortOrder());
            mvo.setCreatedAt(m.getCreatedAt());
            return mvo;
        }).collect(Collectors.toList()));
        return ApiResponse.success(vo);
    }

    // ==================== DTO 转换 ====================

    private PublishTaskVO toVO(PublishTask task) {
        PublishTaskVO vo = new PublishTaskVO();
        vo.setId(task.getId());
        vo.setProjectId(task.getProjectId());
        vo.setPlatforms(task.getPlatforms());
        vo.setPublishText(task.getPublishText());
        vo.setScheduledAt(task.getScheduledAt());
        vo.setStatus(task.getStatus());
        vo.setClaimedBy(task.getClaimedBy());
        vo.setClaimedAt(task.getClaimedAt());
        vo.setCompletedAt(task.getCompletedAt());
        vo.setErrorMessage(task.getErrorMessage());
        vo.setRetryCount(task.getRetryCount());
        vo.setMaxRetry(task.getMaxRetry());
        vo.setRemark(task.getRemark());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());
        return vo;
    }
}
