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

@RestController
@RequestMapping("/task/tasks")
@RequiredArgsConstructor
@Slf4j
public class UserTaskController {

    private final TaskService taskService;

    @GetMapping
    public ApiResponse<Page<Task>> getTaskList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer platform,
            @RequestParam(required = false) Integer type) {
        log.info("任务大厅: page={}, size={}, platform={}, type={}", page, size, platform, type);
        Page<Task> result = taskService.getUserTaskList(page, size, platform, type);
        return ApiResponse.success(result);
    }

    @GetMapping("/{taskId}")
    public ApiResponse<Task> getTaskDetail(@PathVariable Long taskId) {
        Task task = taskService.getTaskDetailForUser(taskId);
        return ApiResponse.success(task);
    }

    @PostMapping("/{taskId}/accept")
    public ApiResponse<Void> acceptTask(
            @AuthenticationPrincipal JwtClaims currentUser,
            @PathVariable Long taskId) {
        if (currentUser == null || currentUser.getUserId() == null)
            return ApiResponse.error(401, "未登录");
        taskService.acceptTask(currentUser.getUserId(), taskId);
        return ApiResponse.success(null, "任务已接取");
    }

    @GetMapping("/records")
    public ApiResponse<Page<Task>> getMyTasks(
            @AuthenticationPrincipal JwtClaims currentUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (currentUser == null || currentUser.getUserId() == null)
            return ApiResponse.error(401, "未登录");
        Page<Task> result = taskService.getMyTaskRecords(currentUser.getUserId(), page, size);
        return ApiResponse.success(result);
    }

    @PostMapping("/{taskId}/submit")
    public ApiResponse<Void> submitTask(
            @AuthenticationPrincipal JwtClaims currentUser,
            @PathVariable Long taskId,
            @RequestBody TaskService.SubmitTaskRequest req) {
        if (currentUser == null || currentUser.getUserId() == null)
            return ApiResponse.error(401, "未登录");
        taskService.submitTask(currentUser.getUserId(), taskId,
                req.getScreenshotUrls(), req.getLatitude(), req.getLongitude());
        return ApiResponse.success(null, "截图已提交，等待审核");
    }

    @GetMapping("/records/{recordId}")
    public ApiResponse<UserTaskRecord> getTaskRecordDetail(
            @AuthenticationPrincipal JwtClaims currentUser,
            @PathVariable Long recordId) {
        if (currentUser == null || currentUser.getUserId() == null)
            return ApiResponse.error(401, "未登录");
        UserTaskRecord record = taskService.getTaskRecordDetail(recordId);
        if (!record.getUserId().equals(currentUser.getUserId()))
            return ApiResponse.error(403, "无权查看此记录");
        return ApiResponse.success(record);
    }

    @GetMapping("/{taskId}/record")
    public ApiResponse<UserTaskRecord> getMyTaskRecord(
            @AuthenticationPrincipal JwtClaims currentUser,
            @PathVariable Long taskId) {
        if (currentUser == null || currentUser.getUserId() == null)
            return ApiResponse.error(401, "未登录");
        UserTaskRecord record = taskService.getTaskRecord(currentUser.getUserId(), taskId);
        return ApiResponse.success(record);
    }

    @PostMapping("/{taskId}/abandon")
    public ApiResponse<Void> abandonTask(
            @AuthenticationPrincipal JwtClaims currentUser,
            @PathVariable Long taskId) {
        if (currentUser == null || currentUser.getUserId() == null)
            return ApiResponse.error(401, "未登录");
        taskService.abandonTask(currentUser.getUserId(), taskId);
        return ApiResponse.success(null, "任务已放弃");
    }

    @PostMapping("/records/{recordId}/review")
    public ApiResponse<UserTaskRecord> reviewTaskRecord(
            @PathVariable Long recordId,
            @RequestParam boolean pass,
            @RequestParam(required = false) String reviewResult) {
        UserTaskRecord record = taskService.reviewTaskRecord(recordId, pass, reviewResult);
        return ApiResponse.success(record, "审核完成");
    }

}
