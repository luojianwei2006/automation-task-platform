package com.task.platform.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.task.platform.admin.entity.Task;
import com.task.platform.admin.mapper.TaskMapper;
import com.task.platform.common.response.ApiResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理后台 - 任务管理接口
 *
 * 超管：全平台任务审核、监控、强制下架
 * 商户管理员：查看/管理自己商户的任务
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

    // ==================== DTO ====================

    @Data
    public static class ReviewRequest {
        private boolean pass;
        private String reason; // pass=false 时必填
    }
}
