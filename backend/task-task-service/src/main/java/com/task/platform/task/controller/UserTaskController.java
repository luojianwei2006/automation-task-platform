package com.task.platform.task.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.task.platform.common.response.ApiResponse;
import com.task.platform.task.entity.Task;
import com.task.platform.task.entity.UserTaskRecord;
import com.task.platform.task.security.JwtClaims;
import com.task.platform.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 用户端任务接口（任务大厅）
 */
@RestController
@RequestMapping("/task/tasks")
@RequiredArgsConstructor
@Slf4j
public class UserTaskController {

    private final TaskService taskService;

    @Value("${file.upload.path:./uploads}")
    private String uploadPath;

    @Value("${file.upload.url-prefix:http://10.0.2.2:8080/api/task/uploads}")
    private String urlPrefix;

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

        taskService.submitTask(currentUser.getUserId(), taskId, req.getScreenshotUrl(), req.getLatitude(), req.getLongitude());
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

    /**
     * 提交任务截图（一步完成：上传文件 + 提交审核）
     * POST /task/tasks/{taskId}/submit-with-upload
     * 权限：普通用户
     * Content-Type: multipart/form-data
     *
     * 请求参数：
     *   - files: 截图文件（可多张）
     *   - latitude:  纬度（可选）
     *   - longitude: 经度（可选）
     */
    @PostMapping(value = "/{taskId}/submit-with-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Void> submitWithUpload(
            @AuthenticationPrincipal JwtClaims currentUser,
            @PathVariable Long taskId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude) {

        if (currentUser == null || currentUser.getUserId() == null) {
            return ApiResponse.error(401, "未登录或Token无效");
        }

        // 校验文件
        if (files == null || files.isEmpty()) {
            return ApiResponse.error(400, "请至少上传一张截图");
        }

        List<String> urls = new ArrayList<>();
        try {
            // 确保上传目录存在
            File uploadDir = new File(uploadPath);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            for (MultipartFile file : files) {
                // 校验文件类型
                String contentType = file.getContentType();
                if (contentType == null || !contentType.startsWith("image/")) {
                    return ApiResponse.error(400, "只能上传图片文件");
                }
                // 校验文件大小（5MB）
                if (file.getSize() > 5 * 1024 * 1024) {
                    return ApiResponse.error(400, "单张图片大小不能超过5MB");
                }

                // 生成唯一文件名
                String originalFilename = file.getOriginalFilename();
                String extension = originalFilename != null
                        ? originalFilename.substring(originalFilename.lastIndexOf("."))
                        : ".jpg";
                String filename = "screenshot_" + UUID.randomUUID() + extension;

                // 保存文件：用手动流拷贝，避免 transferTo() 在 Tomcat 下的路径问题
                File dest = new File(uploadDir, filename);
                dest.getParentFile().mkdirs();
                try (InputStream in = file.getInputStream();
                     FileOutputStream out = new FileOutputStream(dest)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) != -1) {
                        out.write(buf, 0, len);
                    }
                }

                // 拼接访问 URL
                String fileUrl = urlPrefix.endsWith("/")
                        ? urlPrefix + filename
                        : urlPrefix + "/" + filename;
                urls.add(fileUrl);
                log.info("用户截图上传成功: {}", fileUrl);
            }
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return ApiResponse.error(500, "文件上传失败: " + e.getMessage());
        }

        // 提交任务（传入拼接好的 URL 字符串）
        String urlsString = String.join(",", urls);
        taskService.submitTask(currentUser.getUserId(), taskId, urlsString, latitude, longitude);

        return ApiResponse.success(null, "截图已提交，等待审核");
    }
}
