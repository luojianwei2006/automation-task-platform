package com.task.platform.admin.controller;

import com.task.platform.admin.security.AdminUserDetails;
import com.task.platform.admin.service.AdminAuthService;
import com.task.platform.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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

    // ==================== 认证 ====================

    /**
     * 管理员登录
     * POST /api/admin/auth/login
     * 
     * 公开接口，无需 Token
     */
    @PostMapping("/auth/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.success(adminAuthService.login(req.getUsername(), req.getPassword()));
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
}
