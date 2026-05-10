package com.task.platform.task.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.task.platform.common.response.ApiResponse;
import com.task.platform.task.entity.Task;
import com.task.platform.task.security.JwtClaims;
import com.task.platform.task.service.TaskService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 任务服务 - REST API
 *
 * 商户接口：发布任务、查看自己的任务、上下架
 * 用户接口：任务大厅（后续）
 */
@RestController
@RequestMapping("/api/task")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    /**
     * 发布任务
     * POST /api/task
     * 权限：商户管理员
     */
    @PostMapping
    @PreAuthorize("hasRole('MERCHANT_ADMIN')")
    public ApiResponse<Map<String, Object>> publish(
            @AuthenticationPrincipal JwtClaims currentUser,
            @RequestBody TaskService.PublishTaskRequest req) {

        Task task = taskService.publishTask(currentUser.getAdminId(), req);

        Map<String, Object> data = new HashMap<>();
        data.put("taskId", task.getId());
        data.put("status", task.getStatus());
        return ApiResponse.success(data, "任务已提交，等待审核");
    }

    /**
     * 任务列表（分页 + 筛选）
     * GET /api/task?page=1&size=20&status=&platform=&taskType=
     * 商户：只看自己的；超管：看全部
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
    public ApiResponse<Map<String, Object>> listTasks(
            @AuthenticationPrincipal JwtClaims currentUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer platform,
            @RequestParam(required = false) Integer taskType) {

        // 商户只能看自己的任务
        Long merchantId = null;
        if (!currentUser.isSuperAdmin()) {
            merchantId = currentUser.getMerchantId();
        }

        Page<Task> result = taskService.listTasks(page, size, merchantId, status, platform, taskType);

        Map<String, Object> data = new HashMap<>();
        data.put("total", result.getTotal());
        data.put("page", page);
        data.put("size", size);
        data.put("records", result.getRecords());
        return ApiResponse.success(data);
    }

    /**
     * 任务详情
     * GET /api/task/{taskId}
     */
    @GetMapping("/{taskId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
    public ApiResponse<Task> getDetail(
            @AuthenticationPrincipal JwtClaims currentUser,
            @PathVariable Long taskId) {

        Long merchantId = currentUser.isSuperAdmin() ? null : currentUser.getMerchantId();
        Task task = taskService.getTaskDetail(taskId, merchantId);
        return ApiResponse.success(task);
    }

    /**
     * 上下架任务
     * PUT /api/task/{taskId}/status
     * 权限：商户管理员（只能操作自己的任务）
     */
    @PutMapping("/{taskId}/status")
    @PreAuthorize("hasRole('MERCHANT_ADMIN')")
    public ApiResponse<Void> toggleStatus(
            @AuthenticationPrincipal JwtClaims currentUser,
            @PathVariable Long taskId,
            @RequestParam boolean online) {

        taskService.toggleStatus(taskId, currentUser.getMerchantId(), online);
        return ApiResponse.success(null, online ? "任务已上架" : "任务已暂停");
    }
}
