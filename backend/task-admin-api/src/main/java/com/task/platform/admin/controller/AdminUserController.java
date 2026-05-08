package com.task.platform.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.task.platform.admin.entity.AppUser;
import com.task.platform.admin.mapper.AppUserMapper;
import com.task.platform.admin.service.AdminUserService;
import com.task.platform.common.exception.BusinessException;
import com.task.platform.common.response.ApiResponse;
import com.task.platform.common.response.ErrorCode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理后台 - 用户管理接口
 * 提供用户列表、详情、封禁/解封、实名审核功能
 *
 * @author TaskPlatform
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AppUserMapper appUserMapper;
    private final AdminUserService adminUserService;

    /**
     * 用户列表（分页+筛选）
     * GET /api/admin/users?page=1&size=20&phone=&status=&realAuthStatus=
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN', 'FINANCE')")
    public ApiResponse<?> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer realAuthStatus) {

        LambdaQueryWrapper<AppUser> wrapper = new LambdaQueryWrapper<AppUser>()
                .orderByDesc(AppUser::getCreatedAt);

        if (phone != null && !phone.isBlank()) {
            // 模糊匹配（手机号后4位）
            wrapper.like(AppUser::getPhone, phone);
        }
        if (status != null) {
            wrapper.eq(AppUser::getStatus, status);
        }
        if (realAuthStatus != null) {
            wrapper.eq(AppUser::getRealAuthStatus, realAuthStatus);
        }

        IPage<AppUser> pageResult = appUserMapper.selectPage(new Page<>(page, size), wrapper);

        // 脱敏处理
        List<Map<String, Object>> records = pageResult.getRecords().stream()
                .map(this::buildUserVO)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("total", pageResult.getTotal());
        result.put("page", page);
        result.put("size", size);
        result.put("records", records);

        return ApiResponse.success(result);
    }

    /**
     * 用户详情
     * GET /api/admin/users/{userId}
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
    public ApiResponse<?> getUserDetail(@PathVariable Long userId) {
        AppUser user = adminUserService.getUserById(userId);
        return ApiResponse.success(buildUserVO(user));
    }

    /**
     * 封禁/解封用户
     * PUT /api/admin/users/{userId}/status
     */
    @PutMapping("/{userId}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> toggleUserStatus(
            @PathVariable Long userId,
            @RequestBody Map<String, Boolean> body) {

        Boolean enable = body.get("enable");
        if (enable == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "enable参数不能为空");
        }

        adminUserService.toggleUserStatus(userId, enable);
        return ApiResponse.success(null, enable ? "解封成功" : "封禁成功");
    }

    /**
     * 获取用户实名认证详情（管理后台审核用）
     * GET /api/admin/users/{userId}/real-auth
     */
    @GetMapping("/{userId}/real-auth")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
    public ApiResponse<?> getRealAuthDetail(@PathVariable Long userId) {
        return ApiResponse.success(adminUserService.getRealAuthStatus(userId));
    }

    /**
     * 实名认证审核（通过 or 拒绝）
     * POST /api/admin/users/{userId}/real-auth/review
     */
    @PostMapping("/{userId}/real-auth/review")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> reviewRealAuth(
            @PathVariable Long userId,
            @RequestBody ReviewRequest req) {

        adminUserService.reviewRealAuth(userId, req.isPass(), req.getReason());
        return ApiResponse.success(null, req.isPass() ? "认证审核通过" : "认证审核拒绝");
    }

    // ==================== DTO ====================

    @Data
    public static class ReviewRequest {
        private boolean pass;
        private String reason;
    }

    // ==================== 私有工具 ====================

    private Map<String, Object> buildUserVO(AppUser user) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("id", user.getId());
        vo.put("phone", maskPhone(user.getPhone()));
        vo.put("nickname", user.getNickname());
        vo.put("avatarUrl", user.getAvatarUrl());
        vo.put("realAuthStatus", user.getRealAuthStatus() != null ? user.getRealAuthStatus() : 0);
        vo.put("inviteCode", user.getInviteCode());
        vo.put("status", user.getStatus());
        vo.put("createdAt", user.getCreatedAt());
        return vo;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
