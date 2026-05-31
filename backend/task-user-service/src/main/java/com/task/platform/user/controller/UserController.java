package com.task.platform.user.controller;

import com.task.platform.common.response.ApiResponse;
import com.task.platform.user.service.UserProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户信息接口
 * 
 * @author TaskPlatform
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserProfileService userProfileService;

    // ==================== 用户信息 ====================

    /**
     * 获取当前用户信息
     * GET /api/v1/user/profile
     * 需要 Authorization: Bearer {token}
     */
    @GetMapping("/profile")
    public ApiResponse<UserProfileService.UserProfileVO> getProfile(
            @RequestHeader("Authorization") String authorization) {
        Long userId = extractUserId(authorization);
        return ApiResponse.success(userProfileService.getProfile(userId));
    }

    /**
     * 更新用户信息（昵称/头像/微信/支付宝）
     * PUT /api/v1/user/profile
     */
    @PutMapping("/profile")
    public ApiResponse<Void> updateProfile(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody UpdateProfileRequest req) {
        Long userId = extractUserId(authorization);
        userProfileService.updateProfile(userId, req);
        return ApiResponse.success(null);
    }

    /**
     * 绑定/更新收款账户
     * POST /api/v1/wallet/bind
     */
    @PostMapping("/wallet/bind")
    public ApiResponse<Void> bindWallet(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody BindWalletRequest req) {
        Long userId = extractUserId(authorization);
        userProfileService.bindWallet(userId, req);
        return ApiResponse.success(null);
    }

    /**
     * 解绑/删除收款账户
     * DELETE /api/v1/user/wallet/{type}
     * @param type 1=微信 2=支付宝
     */
    @DeleteMapping("/wallet/{type}")
    public ApiResponse<Void> unbindWallet(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Integer type) {
        Long userId = extractUserId(authorization);
        userProfileService.unbindWallet(userId, type);
        return ApiResponse.success(null, "已解绑");
    }

    /**
     * 获取邀请信息（邀请码 + 邀请链接）
     * GET /api/v1/user/invite/link
     */
    @GetMapping("/invite/link")
    public ApiResponse<UserProfileService.InviteLinkVO> getInviteLink(
            @RequestHeader("Authorization") String authorization) {
        Long userId = extractUserId(authorization);
        return ApiResponse.success(userProfileService.getInviteLink(userId));
    }

    /**
     * 获取我的邀请记录
     * GET /api/v1/user/invite/records?page=1&size=20
     */
    @GetMapping("/invite/records")
    public ApiResponse<?> getInviteRecords(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = extractUserId(authorization);
        return ApiResponse.success(userProfileService.getInviteRecords(userId, page, size));
    }

    /**
     * 修改密码
     * PUT /api/user/password
     */
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody ChangePasswordRequest req) {
        Long userId = extractUserId(authorization);
        userProfileService.changePassword(userId, req);
        return ApiResponse.success(null);
    }

    // ==================== DTO ====================

    /** 更新用户信息请求 */
    @Data
    public static class UpdateProfileRequest {

        @Size(min = 1, max = 32, message = "昵称长度1-32位")
        private String nickname;

        @Size(max = 512, message = "头像URL过长")
        private String avatarUrl;
    }

    /** 绑定收款账户请求 */
    @Data
    public static class BindWalletRequest {

        /** 账户类型：1微信 2支付宝 */
        @NotNull(message = "账户类型不能为空")
        private Integer type;

        /** 账户（微信号 or 支付宝账号），传收款码URL时可不传 */
        @Size(max = 128, message = "账户长度超限")
        private String account;

        /** 收款码图片URL（扫码支付时使用） */
        @Size(max = 512, message = "收款码URL长度超限")
        private String qrcodeUrl;
    }

    /** 修改密码请求 */
    @Data
    public static class ChangePasswordRequest {

        @NotBlank(message = "旧密码不能为空")
        private String oldPassword;

        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, message = "新密码长度不能少于6位")
        private String newPassword;
    }

    // ==================== 工具方法 ====================

    /**
     * 从 Authorization 头中提取用户ID
     * 生产环境应在 Filter 层统一解析放入 SecurityContext，此处简化演示
     */
    private Long extractUserId(String authorization) {
        // Authorization: Bearer <token>
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new com.task.platform.common.exception.BusinessException(
                    com.task.platform.common.response.ErrorCode.TOKEN_INVALID);
        }
        String token = authorization.substring(7);
        return com.task.platform.common.utils.JwtUtil.getUserId(token);
    }
}
