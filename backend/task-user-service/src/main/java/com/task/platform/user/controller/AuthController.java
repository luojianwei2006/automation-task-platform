package com.task.platform.user.controller;

import com.task.platform.common.response.ApiResponse;
import com.task.platform.user.service.UserService;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证模块API - 注册/登录/验证码/Token刷新
 * 路径前缀: /api/v1/auth
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 发送短信验证码
     * POST /api/v1/auth/sms/send
     */
    @PostMapping("/sms/send")
    public ApiResponse<Void> sendSms(@RequestBody SmsRequest request) {
        userService.sendSmsCode(request.getPhone(), Integer.valueOf(request.getType()));
        return ApiResponse.success();
    }

    /**
     * 用户注册（密码+验证码）
     * POST /api/v1/auth/register
     */
    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        Map<String, Object> data = userService.register(
                request.getPhone(), request.getCode(),
                request.getPassword(), request.getNickname(),
                request.getInviteCode()
        );
        return ApiResponse.success(data);
    }

    /**
     * 密码登录
     * POST /api/v1/auth/login
     */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginRequest request) {
        Map<String, Object> data = userService.loginWithPassword(
                request.getPhone(), request.getPassword()
        );
        return ApiResponse.success(data);
    }

    /**
     * 验证码登录
     * POST /api/v1/auth/login/sms
     */
    @PostMapping("/login/sms")
    public ApiResponse<Map<String, Object>> loginWithSms(@RequestBody SmsLoginRequest request) {
        Map<String, Object> data = userService.loginWithSms(request.getPhone(), request.getCode());
        return ApiResponse.success(data);
    }

    /**
     * 重置密码
     * POST /api/v1/auth/password/reset
     */
    @PostMapping("/password/reset")
    public ApiResponse<Void> resetPassword(@RequestBody ResetPasswordRequest request) {
        userService.resetPassword(request.getPhone(), request.getCode(), request.getPassword());
        return ApiResponse.success();
    }

    /**
     * 刷新Token
     * POST /api/v1/auth/token/refresh
     */
    @PostMapping("/token/refresh")
    public ApiResponse<Map<String, Object>> refreshToken(@RequestBody RefreshTokenRequest request) {
        Map<String, Object> data = userService.refreshToken(request.getRefreshToken());
        return ApiResponse.success(data);
    }

    // ========== 请求DTO ==========

    @Data
    public static class SmsRequest {
        @NotBlank(message = "手机号不能为空")
        private String phone;
        /** 类型：1注册 2登录 3重置密码 */
        @NotBlank(message = "类型不能为空")
        private String type;
    }

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "手机号不能为空")
        private String phone;
        @NotBlank(message = "验证码不能为空")
        private String code;
        @NotBlank(message = "密码不能为空")
        private String password;
        private String nickname;
        private String inviteCode;
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "手机号不能为空")
        private String phone;
        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    public static class SmsLoginRequest {
        @NotBlank(message = "手机号不能为空")
        private String phone;
        @NotBlank(message = "验证码不能为空")
        private String code;
    }

    @Data
    public static class ResetPasswordRequest {
        @NotBlank(message = "手机号不能为空")
        private String phone;
        @NotBlank(message = "验证码不能为空")
        private String code;
        @NotBlank(message = "新密码不能为空")
        private String password;
    }

    @Data
    public static class RefreshTokenRequest {
        @NotBlank(message = "RefreshToken不能为空")
        private String refreshToken;
    }
}
