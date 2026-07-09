package com.task.platform.admin.controller;

import com.task.platform.admin.security.AdminUserDetails;
import com.task.platform.admin.service.AdminAuthService;
import com.task.platform.admin.util.CaptchaUtil;
import com.task.platform.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 管理后台认证 & 子账号管理接口
 *
 * @author TaskPlatform
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String CAPTCHA_PREFIX = "admin:captcha:";
    private static final long CAPTCHA_TTL_SECONDS = 120; // 验证码 2 分钟有效

    // ==================== 认证 ====================

    /**
     * 获取图形验证码
     * GET /api/admin/auth/captcha
     * 
     * 公开接口，无需 Token
     * 返回 { captchaKey, captchaImage } 
     */
    @GetMapping("/auth/captcha")
    public ApiResponse<Map<String, String>> captcha() {
        CaptchaUtil.CaptchaResult result = CaptchaUtil.generate(120, 40);
        String key = UUID.randomUUID().toString().replace("-", "");
        
        // 存入 Redis，2 分钟过期
        stringRedisTemplate.opsForValue().set(
                CAPTCHA_PREFIX + key,
                result.getCode(),
                CAPTCHA_TTL_SECONDS,
                TimeUnit.SECONDS
        );

        Map<String, String> data = Map.of(
                "captchaKey", key,
                "captchaImage", result.getBase64()
        );
        return ApiResponse.success(data);
    }

    /**
     * 管理员登录
     * POST /api/admin/auth/login
     * 
     * 公开接口，无需 Token
     */
    @PostMapping("/auth/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest req) {
        // 校验验证码
        if (req.getCaptchaKey() == null || req.getCaptchaCode() == null) {
            return ApiResponse.error(400, "请输入验证码");
        }
        String cacheCode = stringRedisTemplate.opsForValue().get(CAPTCHA_PREFIX + req.getCaptchaKey());
        if (cacheCode == null) {
            return ApiResponse.error(400, "验证码已过期，请刷新");
        }
        if (!cacheCode.equalsIgnoreCase(req.getCaptchaCode().trim())) {
            return ApiResponse.error(400, "验证码错误");
        }
        // 验证通过，删除缓存（一次性使用）
        stringRedisTemplate.delete(CAPTCHA_PREFIX + req.getCaptchaKey());

        return ApiResponse.success(adminAuthService.login(req.getUsername(), req.getPassword()));
    }

    /**
     * 获取当前登录用户信息
     * GET /api/admin/auth/me
     */
    @GetMapping("/auth/me")
    public ApiResponse<Map<String, Object>> me(@AuthenticationPrincipal AdminUserDetails userDetails) {
        var u = userDetails.getAdminUser();
        Map<String, Object> info = new java.util.HashMap<>();
        info.put("id", u.getId());
        info.put("username", u.getUsername());
        info.put("displayName", u.getDisplayName());
        info.put("roleType", u.getRoleType());
        info.put("merchantId", u.getMerchantId());
        return ApiResponse.success(info);
    }

    /**
     * 修改密码
     * PUT /api/admin/auth/change-password
     */
    @PutMapping("/auth/change-password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal AdminUserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest req) {
        adminAuthService.changePassword(userDetails.getAdminId(), req.getOldPassword(), req.getNewPassword());
        return ApiResponse.success(null, "密码修改成功");
    }

    // ==================== 子账号管理 ====================

    /**
     * 创建子账号
     * POST /api/admin/accounts
     * 
     * 权限：超管 or 商户管理员
     */
    @PostMapping("/accounts")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
    public ApiResponse<Void> createAccount(
            @AuthenticationPrincipal AdminUserDetails currentUser,
            @Valid @RequestBody CreateAccountRequest req) {

        AdminAuthService.CreateAccountRequest serviceReq = new AdminAuthService.CreateAccountRequest();
        serviceReq.setUsername(req.getUsername());
        serviceReq.setPassword(req.getPassword());
        serviceReq.setDisplayName(req.getDisplayName());
        serviceReq.setRoleType(req.getRoleType());
        serviceReq.setMerchantId(req.getMerchantId());

        adminAuthService.createSubAccount(
                currentUser.getAdminId(),
                currentUser.getAdminUser().getRoleType(),
                serviceReq
        );
        return ApiResponse.success(null, "创建成功");
    }

    /**
     * 启用/禁用账号
     * PUT /api/admin/accounts/{id}/status
     */
    @PutMapping("/accounts/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
    public ApiResponse<Void> toggleStatus(
            @AuthenticationPrincipal AdminUserDetails currentUser,
            @PathVariable Long id,
            @RequestParam boolean enable) {

        adminAuthService.toggleAccountStatus(
                currentUser.getAdminId(),
                currentUser.getAdminUser().getRoleType(),
                id, enable
        );
        return ApiResponse.success(null, enable ? "账号已启用" : "账号已禁用");
    }

    /**
     * 获取商户子账号列表
     * GET /api/admin/merchant/{merchantId}/accounts
     */
    @GetMapping("/merchant/{merchantId}/accounts")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
    public ApiResponse<?> listMerchantAccounts(@PathVariable Long merchantId) {
        return ApiResponse.success(adminAuthService.listMerchantSubAccounts(merchantId));
    }

    // ==================== DTO ====================

    @Data
    public static class LoginRequest {
        @NotBlank(message = "账号不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;

        /** 验证码 Key（从 /auth/captcha 接口获取） */
        @NotBlank(message = "验证码不能为空")
        private String captchaKey;

        /** 验证码（用户输入的） */
        @NotBlank(message = "验证码不能为空")
        private String captchaCode;
    }

    @Data
    public static class CreateAccountRequest {
        @NotBlank(message = "账号不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 32, message = "密码长度6-32位")
        private String password;

        @NotBlank(message = "显示名称不能为空")
        private String displayName;

        private Integer roleType;
        private Long merchantId;
    }

    @Data
    public static class ChangePasswordRequest {
        @NotBlank(message = "旧密码不能为空")
        private String oldPassword;
        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 32, message = "密码长度6-32位")
        private String newPassword;
    }
}
