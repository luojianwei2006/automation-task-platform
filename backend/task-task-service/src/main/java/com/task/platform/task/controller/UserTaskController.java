package com.task.platform.task.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.task.platform.common.response.ApiResponse;
import com.task.platform.task.entity.Task;
import com.task.platform.task.entity.UserTaskRecord;
import com.task.platform.task.security.JwtClaims;
import com.task.platform.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户端任务接口（任务大厅）
 */
@RestController
@RequestMapping("/task/tasks")
@RequiredArgsConstructor
@Slf4j
public class UserTaskController {

    private final TaskService taskService;

    /**
     * 任务列表（任务大厅）
     * GET /task/tasks?page=1&size=20&platform=&type=
     * 只返回上架中的任务
     */
    @GetMapping
    public ApiResponse<Page<Task>> getTaskList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer platform,
            @RequestParam(required = false) Integer type) {

        log.info("===== 任务大厅请求 =====");
        log.info("page={}, size={}, platform={}, type={}", page, size, platform, type);

        Page<Task> result = taskService.getUserTaskList(page, size, platform, type);

        log.info("查询结果：total={}, records={}", result.getTotal(), result.getRecords().size());

        return ApiResponse.success(result);
    }

    /**
     * 任务详情
     * GET /task/tasks/{taskId}
     */
    @GetMapping("/{taskId}")
    public ApiResponse<Task> getTaskDetail(@PathVariable Long taskId) {
        Task task = taskService.getTaskDetailForUser(taskId);
        return ApiResponse.success(task);
    }

    /**
     * 接受任务
     * POST /task/tasks/{taskId}/accept
     * 权限：普通用户
     */
    @PostMapping("/{taskId}/accept")
    public ApiResponse<Void> acceptTask(
            @AuthenticationPrincipal JwtClaims currentUser,
            @PathVariable Long taskId) {

        if (currentUser == null || currentUser.getUserId() == null) {
            return ApiResponse.error(401, "未登录或Token无效");
        }

        taskService.acceptTask(currentUser.getUserId(), taskId);
        return ApiResponse.success(null, "任务已接取");
    }

    /**
     * 我的任务记录
     * GET /task/tasks/records?page=1&size=20
     */
    @GetMapping("/records")
    public ApiResponse<Page<Task>> getMyTasks(
            @AuthenticationPrincipal JwtClaims currentUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (currentUser == null || currentUser.getUserId() == null) {
            return ApiResponse.error(401, "未登录或Token无效");
        }

        Page<Task> result = taskService.getMyTaskRecords(currentUser.getUserId(), page, size);
        return ApiResponse.success(result);
    }

    /**
     * 提交任务截图
     * POST /task/tasks/{taskId}/submit
     * 权限：普通用户
     */
    @PostMapping("/{taskId}/submit")
    public ApiResponse<Void> submitTask(
            @AuthenticationPrincipal JwtClaims currentUser,
            @PathVariable Long taskId,
            @RequestBody TaskService.SubmitTaskRequest req) {

        if (currentUser == null || currentUser.getUserId() == null) {
            return ApiResponse.error(401, "未登录或Token无效");
        }

        taskService.submitTask(currentUser.getUserId(), taskId, req.getScreenshotUrls(), req.getLatitude(), req.getLongitude());
        return ApiResponse.success(null, "截图已提交，等待审核");
    }

    /**
     * 查询任务记录详情（审核进度时间线）
     * GET /task/tasks/records/{recordId}
     */
    @GetMapping("/records/{recordId}")
    public ApiResponse<UserTaskRecord> getTaskRecordDetail(
            @AuthenticationPrincipal JwtClaims currentUser,
            @PathVariable Long recordId) {

        if (currentUser == null || currentUser.getUserId() == null) {
            return ApiResponse.error(401, "未登录或Token无效");
        }

        UserTaskRecord record = taskService.getTaskRecordDetail(recordId);

        // 只能查看自己的任务记录
        if (!record.getUserId().equals(currentUser.getUserId())) {
            return ApiResponse.error(403, "无权查看此记录");
        }

        return ApiResponse.success(record);
    }

    /**
     * 获取当前用户对指定任务的记录
     * GET /task/tasks/{taskId}/record
     * 用于任务详情页判断用户是否已接取该任务
     */
    @GetMapping("/{taskId}/record")
    public ApiResponse<UserTaskRecord> getMyTaskRecord(
            @AuthenticationPrincipal JwtClaims currentUser,
            @PathVariable Long taskId) {

        if (currentUser == null || currentUser.getUserId() == null) {
            return ApiResponse.error(401, "未登录或Token无效");
        }

        // 根据 userId 和 taskId 查询记录
        UserTaskRecord record = taskService.getTaskRecord(currentUser.getUserId(), taskId);

        return ApiResponse.success(record);
    }

    /**
     * 放弃任务（用户主动放弃进行中的任务）
     * POST /task/tasks/{taskId}/abandon
     */
    @PostMapping("/{taskId}/abandon")
    public ApiResponse<Void> abandonTask(
            @AuthenticationPrincipal JwtClaims currentUser,
            @PathVariable Long taskId) {

        if (currentUser == null || currentUser.getUserId() == null) {
            return ApiResponse.error(401, "未登录或Token无效");
        }

        taskService.abandonTask(currentUser.getUserId(), taskId);
        return ApiResponse.success(null, "任务已放弃");
    }

    /**
     * 审核任务记录（人工审核，预留AI审核接口）
     * POST /task/tasks/records/{recordId}/review
     * 权限：管理后台
     */
    @PostMapping("/records/{recordId}/review")
    public ApiResponse<UserTaskRecord> reviewTaskRecord(
            @PathVariable Long recordId,
            @RequestParam boolean pass,
            @RequestParam(required = false) String reviewResult) {

        UserTaskRecord record = taskService.reviewTaskRecord(recordId, pass, reviewResult);
        return ApiResponse.success(record, "审核完成");
    }

}
