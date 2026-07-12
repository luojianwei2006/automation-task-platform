package com.task.platform.admin.controller;

import com.task.platform.admin.dto.publish.*;
import com.task.platform.admin.entity.PublishMaterial;
import com.task.platform.admin.entity.PublishTask;
import com.task.platform.admin.entity.UserPublishRecord;
import com.task.platform.admin.mapper.UserPublishRecordMapper;
import com.task.platform.admin.service.MobilePublishService;
import com.task.platform.admin.service.PublishMaterialService;
import com.task.platform.admin.service.PublishProjectService;
import com.task.platform.admin.service.PublishTaskService;
import com.task.platform.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RequiredArgsConstructor
public class MobilePublishController {

    private final MobilePublishService mobilePublishService;
    private final PublishProjectService publishProjectService;
    private final PublishMaterialService publishMaterialService;
    private final UserPublishRecordMapper userPublishRecordMapper;

    /**
     * 可领取任务列表（pending + 当前用户已claimed）
     * GET /api/mobile/publish/tasks?userId=
     */
    @GetMapping
    public ApiResponse<List<PublishTaskVO>> getAvailableTasks(@RequestHeader("X-User-Id") Long userId) {
        List<PublishTask> tasks = mobilePublishService.getAvailableTasks(userId);
        List<PublishTaskVO> vos = tasks.stream().map(t -> toVO(t, userId)).collect(Collectors.toList());
        return ApiResponse.success(vos);
    }

    /**
     * 更新发布任务状态（废弃，改用 /{id}/claim）
     */
    @Deprecated
    @PostMapping("/{id}/status")
    public ApiResponse<Void> updateTaskStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.error(400, "此接口已废弃，请使用 /claim + /publish + /submit");
    }

    /**
     * 我的任务（已领取/执行中）
     * GET /api/mobile/publish/tasks/my?userId=
     */
    @GetMapping("/my")
    public ApiResponse<List<PublishTaskVO>> getMyTasks(@RequestHeader("X-User-Id") Long userId) {
        List<PublishTask> tasks = mobilePublishService.getMyTasks(userId);
        List<PublishTaskVO> vos = tasks.stream().map(t -> toVO(t, userId)).collect(Collectors.toList());
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
        return toVO(task, null);
    }

    private PublishTaskVO toVO(PublishTask task, Long userId) {
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
        vo.setPublishedAt(task.getPublishedAt());
        // 填充项目名
        if (task.getProjectId() != null) {
            try {
                var project = publishProjectService.getById(task.getProjectId());
                vo.setProjectName(project != null ? project.getName() : "");
            } catch (Exception e) {
                vo.setProjectName("");
            }
        }
        vo.setErrorMessage(task.getErrorMessage());
        vo.setRetryCount(task.getRetryCount());
        vo.setMaxRetry(task.getMaxRetry());
        vo.setRemark(task.getRemark());
        vo.setRewardAmount(task.getRewardAmount());
        // 填充配额（移动端列表/详情需展示剩余/总配额，漏填会导致安卓显示 0/0）
        vo.setTotalQuota(task.getTotalQuota() != null ? task.getTotalQuota() : 0);
        vo.setUsedQuota(task.getUsedQuota() != null ? task.getUsedQuota() : 0);
        vo.setImages(task.getImages());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());
        // 注入当前用户的提交记录状态（用于列表精确展示 6 态）
        if (userId != null) {
            UserPublishRecord rec = userPublishRecordMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserPublishRecord>()
                    .eq(UserPublishRecord::getUserId, userId)
                    .eq(UserPublishRecord::getTaskId, task.getId())
                    .orderByDesc(UserPublishRecord::getId)
                    .last("LIMIT 1")
            );
            if (rec != null) {
                vo.setSubmissionStatus(rec.getStatus());
            }
        }
        return vo;
    }

    // =================== 任务领取与发布 ===================

    /**
     * 领取任务
     */
    @PostMapping("/{id}/claim")
    public ApiResponse<Void> claim(@PathVariable Long id, @RequestHeader("X-User-Id") Long userId) {
        PublishTask task = mobilePublishService.getTaskById(id);
        if (task == null) {
            return ApiResponse.error(404, "任务不存在");
        }
        Long existCount = userPublishRecordMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserPublishRecord>()
                .eq(UserPublishRecord::getUserId, userId)
                .eq(UserPublishRecord::getTaskId, id)
        );
        if (existCount > 0) {
            return ApiResponse.error(400, "你已经领取过该任务");
        }
        UserPublishRecord record = new UserPublishRecord();
        record.setUserId(userId);
        log.info("[DEBUG] claim: userId={}, taskId={}", userId, id);
        record.setTaskId(id);
        record.setStatus("CLAIMED");
        userPublishRecordMapper.insert(record);
        // 领取即占用配额，使剩余配额 = 总配额 - 已领取数
        int uq = (task.getUsedQuota() != null ? task.getUsedQuota() : 0) + 1;
        task.setUsedQuota(uq);
        mobilePublishService.updateTask(task);
        return ApiResponse.success(null, "领取成功");
    }

    /**
     * 发布任务（完成后拉起分享）
     */
    @PostMapping("/{id}/publish")
    public ApiResponse<Void> publish(@PathVariable Long id, @RequestHeader("X-User-Id") Long userId) {
        PublishTask task = mobilePublishService.getTaskById(id);
        if (task == null) {
            return ApiResponse.error(404, "任务不存在");
        }
        UserPublishRecord record = userPublishRecordMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserPublishRecord>()
                .eq(UserPublishRecord::getUserId, userId)
                .eq(UserPublishRecord::getTaskId, id)
        );
        if (record == null) {
            return ApiResponse.error(400, "请先领取任务");
        }
        task.setStatus(PublishTaskService.STATUS_COMPLETED);
        mobilePublishService.updateTask(task);
        log.info("[DEBUG] publish: userId={}, taskId={}", userId, id);
        record.setStatus("MERGED");
        userPublishRecordMapper.updateById(record);
        return ApiResponse.success(null, "发布成功");
    }

    /**
     * 提交审核（上传截图后）
     */
    @PostMapping("/{id}/submit")
    public ApiResponse<Void> submitReview(@PathVariable Long id,
                                          @RequestHeader("X-User-Id") Long userId,
                                          @RequestBody SubmitReviewReq req) {
        UserPublishRecord record = userPublishRecordMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserPublishRecord>()
                .eq(UserPublishRecord::getUserId, userId)
                .eq(UserPublishRecord::getTaskId, id)
        );
        if (record == null) {
            return ApiResponse.error(400, "请先领取任务");
        }
        if (req.getScreenshots() == null || req.getScreenshots().isEmpty()) {
            return ApiResponse.error(400, "请至少上传1张截图");
        }
        record.setScreenshots(String.join(",", req.getScreenshots()));
        record.setMergedVideoUrl(req.getMergedVideoUrl());
        log.info("[DEBUG] submit: userId={}, taskId={}, screenshots={}", userId, id, req.getScreenshots());
        record.setStatus("SUBMITTED");
        record.setSubmittedAt(java.time.LocalDateTime.now());
        userPublishRecordMapper.updateById(record);
        return ApiResponse.success(null, "提交成功，等待审核");
    }

    /** 查询提交状态 */
    @GetMapping("/{id}/submission-status")
    public ApiResponse<UserPublishRecord> getSubmissionStatus(@PathVariable Long id, @RequestHeader("X-User-Id") Long userId) {
        log.info("[DEBUG] submission-status: userId={}, taskId={}", userId, id);
        UserPublishRecord record = userPublishRecordMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserPublishRecord>()
                .eq(UserPublishRecord::getUserId, userId)
                .eq(UserPublishRecord::getTaskId, id)
        );
        if (record == null) {
            return ApiResponse.error(404, "未领取");
        }
        return ApiResponse.success(record);
    }
}
