package com.task.platform.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.task.platform.admin.entity.Task;
import com.task.platform.admin.mapper.TaskMapper;
import com.task.platform.admin.security.AdminUserDetails;
import com.task.platform.common.response.ApiResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理后台 - 任务管理接口
 *
 * 超管：全平台任务审核、监控、强制下架、发布任务、编辑任务
 * 商户管理员：查看/管理自己商户的任务、发布任务、编辑任务
 */
@RestController
@RequestMapping("/api/admin/tasks")
@RequiredArgsConstructor
public class AdminTaskController {

    private final TaskMapper taskMapper;

    // 状态常量
    private static final int STATUS_PENDING  = 0;
    private static final int STATUS_ONLINE   = 1;
    private static final int STATUS_PAUSED   = 2;
    private static final int STATUS_ENDED    = 3;
    private static final int STATUS_REJECTED = 4;

    // ==================== 任务列表 ====================

    /**
     * 任务列表（分页 + 筛选）
     * GET /api/admin/tasks?page=1&size=20&status=&platform=&taskType=&merchantId=
     * 超管：可查看全部，可传 merchantId 筛选
     * 商户管理员：只能看自己的
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
    public ApiResponse<Map<String, Object>> listTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer platform,
            @RequestParam(required = false) Integer taskType,
            @RequestParam(required = false) Long merchantId) {

        // TODO: 从 JWT 获取当前用户角色和 merchantId
        //  商户管理员强制只能看自己的任务（merchantId = 自己的 merchantId）
        //  超管可以传 merchantId 参数筛选
        //  暂时不实现权限过滤，后续补充

        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<Task>()
                .orderByDesc(Task::getCreatedAt);

        if (status != null) {
            wrapper.eq(Task::getStatus, status);
        }
        if (platform != null) {
            wrapper.eq(Task::getPlatform, platform);
        }
        if (taskType != null) {
            wrapper.eq(Task::getTaskType, taskType);
        }
        if (merchantId != null) {
            wrapper.eq(Task::getMerchantId, merchantId);
        }

        IPage<Task> result = taskMapper.selectPage(new Page<>(page, size), wrapper);

        Map<String, Object> data = new HashMap<>();
        data.put("total", result.getTotal());
        data.put("page", page);
        data.put("size", size);
        data.put("records", result.getRecords());
        return ApiResponse.success(data);
    }

    // ==================== 任务审核 ====================

    /**
     * 审核任务（通过/拒绝）
     * PUT /api/admin/tasks/{taskId}/review
     * 权限：超管
     */
    @PutMapping("/{taskId}/review")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> reviewTask(
            @PathVariable Long taskId,
            @RequestBody ReviewRequest req) {

        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            return ApiResponse.error(404, "任务不存在");
        }

        if (req.isPass()) {
            task.setStatus(STATUS_ONLINE);
            task.setPublishedAt(java.time.LocalDateTime.now());
        } else {
            task.setStatus(STATUS_REJECTED);
            task.setRejectReason(req.getReason());
        }
        taskMapper.updateById(task);
        return ApiResponse.success(null, req.isPass() ? "审核通过，任务已上架" : "审核拒绝");
    }

    // ==================== 强制下架 ====================

    /**
     * 强制下架任务
     * PUT /api/admin/tasks/{taskId}/toggle
     * 权限：超管
     */
    @PutMapping("/{taskId}/toggle")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> forceToggle(
            @PathVariable Long taskId,
            @RequestParam boolean online) {

        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            return ApiResponse.error(404, "任务不存在");
        }

        task.setStatus(online ? STATUS_ONLINE : STATUS_PAUSED);
        taskMapper.updateById(task);
        return ApiResponse.success(null, online ? "任务已上架" : "任务已下架");
    }

    // ==================== 发布任务 ====================

    /**
     * 发布任务
     * POST /api/admin/tasks
     * 权限：超管（可指定商户）、商户管理员（只能为自己商户发布）
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
    public ApiResponse<Map<String, Object>> publishTask(
            @AuthenticationPrincipal AdminUserDetails currentUser,
            @RequestBody PublishTaskRequest req) {

        Long merchantId;
        if (currentUser.isSuperAdmin()) {
            // 超管可以指定商户ID
            merchantId = req.getMerchantId();
            if (merchantId == null) {
                return ApiResponse.error(400, "超管发布任务时必须指定merchantId");
            }
        } else {
            // 商户管理员只能为自己商户发布
            merchantId = currentUser.getMerchantId();
        }

        Task task = new Task();
        task.setMerchantId(merchantId);
        task.setTitle(req.getTitle());
        task.setPlatform(req.getPlatform());
        task.setTaskType(req.getTaskType());
        task.setTargetUrl(req.getTargetUrl());
        task.setRequirements(req.getRequirements());
        task.setRequirementImages(req.getRequirementImages());
        task.setRewardAmount(req.getRewardAmount());
        task.setTotalQuota(req.getTotalQuota());
        task.setUsedQuota(0);
        task.setDailyLimit(req.getDailyLimit() != null ? req.getDailyLimit() : 0);
        task.setStatus(STATUS_PENDING); // 待审核
        task.setBudgetPoints(req.getBudgetPoints());
        task.setUsedPoints(BigDecimal.ZERO);
        task.setDeadline(req.getDeadline());

        taskMapper.insert(task);

        Map<String, Object> data = new HashMap<>();
        data.put("taskId", task.getId());
        data.put("status", task.getStatus());
        return ApiResponse.success(data, "任务已提交，等待审核");
    }

    // ==================== 编辑任务 ====================

    /**
     * 编辑任务
     * PUT /api/admin/tasks/{taskId}
     * 权限：超管（可编辑任何任务）、商户管理员（只能编辑自己商户的任务）
     */
    @PutMapping("/{taskId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
    public ApiResponse<Void> updateTask(
            @AuthenticationPrincipal AdminUserDetails currentUser,
            @PathVariable Long taskId,
            @RequestBody UpdateTaskRequest req) {

        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            return ApiResponse.error(404, "任务不存在");
        }

        // 权限检查：商户管理员只能编辑自己商户的任务
        if (!currentUser.isSuperAdmin()) {
            if (!task.getMerchantId().equals(currentUser.getMerchantId())) {
                return ApiResponse.error(403, "无权编辑此任务");
            }
        }

        // 更新字段（只更新非null的字段）
        if (req.getTitle() != null) {
            task.setTitle(req.getTitle());
        }
        if (req.getPlatform() != null) {
            task.setPlatform(req.getPlatform());
        }
        if (req.getTaskType() != null) {
            task.setTaskType(req.getTaskType());
        }
        if (req.getTargetUrl() != null) {
            task.setTargetUrl(req.getTargetUrl());
        }
        if (req.getRequirements() != null) {
            task.setRequirements(req.getRequirements());
        }
        if (req.getRequirementImages() != null) {
            task.setRequirementImages(req.getRequirementImages());
        }
        if (req.getRewardAmount() != null) {
            task.setRewardAmount(req.getRewardAmount());
        }
        if (req.getTotalQuota() != null) {
            task.setTotalQuota(req.getTotalQuota());
        }
        if (req.getDailyLimit() != null) {
            task.setDailyLimit(req.getDailyLimit());
        }
        if (req.getBudgetPoints() != null) {
            task.setBudgetPoints(req.getBudgetPoints());
        }
        if (req.getDeadline() != null) {
            task.setDeadline(req.getDeadline());
        }

        taskMapper.updateById(task);
        return ApiResponse.success(null, "任务已更新");
    }

    // ==================== DTO ====================

    @Data
    public static class ReviewRequest {
        private boolean pass;
        private String reason; // pass=false 时必填
    }

    @Data
    public static class PublishTaskRequest {
        /** 商户ID（超管必填，商户管理员不传） */
        private Long merchantId;
        /** 任务标题（必填） */
        private String title;
        /** 平台：1抖音 2小红书（必填） */
        private Integer platform;
        /** 任务类型：1点赞 2评论（必填） */
        private Integer taskType;
        /** 目标链接（必填） */
        private String targetUrl;
        /** 任务要求（文字说明） */
        private String requirements;
        /** 任务要求图片（JSON数组） */
        private String requirementImages;
        /** 单次奖励金额（必填） */
        private java.math.BigDecimal rewardAmount;
        /** 总完成次数上限（必填） */
        private Integer totalQuota;
        /** 每日完成上限（0=不限，默认0） */
        private Integer dailyLimit;
        /** 预算点数（含15%服务费，必填） */
        private java.math.BigDecimal budgetPoints;
        /** 截止时间 */
        private java.time.LocalDateTime deadline;
    }

    @Data
    public static class UpdateTaskRequest {
        /** 任务标题 */
        private String title;
        /** 平台：1抖音 2小红书 */
        private Integer platform;
        /** 任务类型：1点赞 2评论 */
        private Integer taskType;
        /** 目标链接 */
        private String targetUrl;
        /** 任务要求（文字说明） */
        private String requirements;
        /** 任务要求图片（JSON数组） */
        private String requirementImages;
        /** 单次奖励金额 */
        private java.math.BigDecimal rewardAmount;
        /** 总完成次数上限 */
        private Integer totalQuota;
        /** 每日完成上限（0=不限） */
        private Integer dailyLimit;
        /** 预算点数（含15%服务费） */
        private java.math.BigDecimal budgetPoints;
        /** 截止时间 */
        private java.time.LocalDateTime deadline;
    }
}
