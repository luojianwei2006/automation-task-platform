package com.task.platform.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.task.platform.admin.dto.publish.CreatePublishTaskReq;
import com.task.platform.admin.dto.publish.MaterialListVO;
import com.task.platform.admin.dto.publish.PublishTaskVO;
import com.task.platform.admin.dto.publish.UpdatePublishTaskReq;
import com.task.platform.admin.entity.PublishMaterial;
import com.task.platform.admin.entity.PublishTask;
import com.task.platform.admin.service.PublishTaskService;
import com.task.platform.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 发布任务接口（视频发布功能）
 *
 * @author TaskPlatform
 */
@RestController
@RequestMapping("/publish/tasks")
@RequiredArgsConstructor
public class PublishTaskController {

    private final PublishTaskService publishTaskService;

    /**
     * 创建发布任务
     * POST /api/publish/tasks
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody CreatePublishTaskReq req) {
        if (req.getProjectId() == null) {
            return ApiResponse.error(400, "projectId 不能为空");
        }
        if (req.getPlatforms() == null || req.getPlatforms().isBlank()) {
            return ApiResponse.error(400, "platforms 不能为空");
        }

        try {
            PublishTask task = publishTaskService.create(req);
            Map<String, Object> data = new HashMap<>();
            data.put("id", task.getId());
            data.put("status", task.getStatus());
            return ApiResponse.success(data, "发布任务创建成功");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 任务列表（分页 + 状态筛选）
     * GET /api/publish/tasks?page=1&size=20&status=
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {

        IPage<PublishTask> result = publishTaskService.list(page, size, status);
        Map<String, Object> data = new HashMap<>();
        data.put("total", result.getTotal());
        data.put("page", page);
        data.put("size", size);
        data.put("records", result.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return ApiResponse.success(data);
    }

    /**
     * 任务详情（含项目素材）
     * GET /api/publish/tasks/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<PublishTaskVO> getById(@PathVariable Long id) {
        PublishTask task = publishTaskService.getById(id);
        if (task == null) {
            return ApiResponse.error(404, "任务不存在");
        }

        PublishTaskVO vo = toVO(task);
        // 填充关联素材
        List<PublishMaterial> materials = publishTaskService.getTaskMaterials(id);
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

    /**
     * 更新任务
     * PUT /api/publish/tasks/{id}
     */
    @PutMapping("/{id}")
    public ApiResponse<PublishTaskVO> update(@PathVariable Long id,
                                              @RequestBody UpdatePublishTaskReq req) {
        PublishTask task = publishTaskService.update(id, req);
        if (task == null) {
            return ApiResponse.error(404, "任务不存在");
        }
        return ApiResponse.success(toVO(task), "任务已更新");
    }

    /**
     * 取消任务（仅pending）
     * DELETE /api/publish/tasks/{id}
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        try {
            boolean ok = publishTaskService.cancel(id);
            if (!ok) {
                return ApiResponse.error(404, "任务不存在");
            }
            return ApiResponse.success(null, "任务已取消");
        } catch (IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        }
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
