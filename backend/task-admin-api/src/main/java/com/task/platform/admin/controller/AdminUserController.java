package com.task.platform.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.task.platform.admin.entity.AppUser;
import com.task.platform.admin.mapper.AppUserMapper;
import com.task.platform.admin.mapper.UserEarningsMapper;
import com.task.platform.admin.security.AdminUserDetails;
import com.task.platform.admin.service.AdminUserService;
import com.task.platform.common.exception.BusinessException;
import com.task.platform.common.response.ApiResponse;
import com.task.platform.common.response.ErrorCode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
@Slf4j
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AppUserMapper appUserMapper;
    private final AdminUserService adminUserService;
    private final UserEarningsMapper userEarningsMapper;

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

        // 批量填充余额（手机号/身份证号均返回明文，不再脱敏）
        List<Long> userIds = pageResult.getRecords().stream()
                .map(AppUser::getId)
                .collect(Collectors.toList());
        Map<Long, BigDecimal> balanceMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            try {
                List<Map<String, Object>> balanceRows = userEarningsMapper.selectLatestBalanceBatch(userIds);
                for (Map<String, Object> row : balanceRows) {
                    Long uid = ((Number) row.get("user_id")).longValue();
                    BigDecimal bal = row.get("balance_after") != null
                            ? new BigDecimal(row.get("balance_after").toString())
                            : null;
                    balanceMap.put(uid, bal);
                }
            } catch (Exception ignored) {
                // 批量查询失败时降级：不显示余额
            }
        }

        List<Map<String, Object>> records = pageResult.getRecords().stream()
                .map(user -> buildUserVO(user, balanceMap.get(user.getId())))
                .collect(Collectors.toList());

        // 调试日志：打印第一条记录的关键字段
        if (!records.isEmpty()) {
            Map<String, Object> first = records.get(0);
            log.info("[listUsers] 样例记录: id={}, realAuthStatus={}, realName={}, idCard={}",
                    first.get("id"), first.get("realAuthStatus"), first.get("realName"), first.get("idCard"));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", pageResult.getTotal());
        result.put("page", page);
        result.put("size", size);
        result.put("records", records);

        return ApiResponse.success(result);
    }

    /**
     * 用户详情（编辑用，手机号不过敏）
     * GET /api/admin/users/{userId}
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
    public ApiResponse<?> getUserDetail(@PathVariable Long userId) {
        AppUser user = adminUserService.getUserById(userId);
        return ApiResponse.success(buildUserDetailVO(user));
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
            @AuthenticationPrincipal AdminUserDetails currentUser,
            @PathVariable Long userId,
            @RequestBody ReviewRequest req) {

        adminUserService.reviewRealAuth(userId, req.isPass(), req.getReason(),
                currentUser != null ? currentUser.getAdminId() : null);
        return ApiResponse.success(null, req.isPass() ? "认证审核通过" : "认证审核拒绝");
    }

    /**
     * 管理员新增C端用户
     * POST /api/admin/users
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<?> createUser(@RequestBody CreateUserRequest req) {
        AppUser user = adminUserService.createUser(
                req.getPhone(), req.getPassword(), req.getNickname());
        Map<String, Object> vo = buildUserVO(user, null);
        return ApiResponse.success(vo, "用户创建成功");
    }

    /**
     * 管理员编辑C端用户（可重置密码）
     * PUT /api/admin/users/{userId}
     */
    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<?> updateUser(
            @PathVariable Long userId,
            @RequestBody UpdateUserRequest req) {
        adminUserService.updateUser(
                userId, req.getNickname(), req.getNewPassword(), req.getStatus());
        return ApiResponse.success(null, "用户更新成功");
    }

    // ==================== DTO ====================

    @Data
    public static class ReviewRequest {
        private boolean pass;
        private String reason;
    }

    /** 新增用户请求 */
    @Data
    public static class CreateUserRequest {
        private String phone;       // 手机号（必填）
        private String password;    // 密码明文（必填）
        private String nickname;     // 昵称（可选）
    }

    /** 编辑用户请求 */
    @Data
    public static class UpdateUserRequest {
        private String nickname;     // 昵称（可选，null=不修改）
        private String newPassword;  // 新密码明文（可选，null/空=不修改）
        private Integer status;      // 账号状态（可选，null=不修改）
    }

    // ==================== 私有工具 ====================

    private Map<String, Object> buildUserVO(AppUser user, BigDecimal balance) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("id", user.getId());
        vo.put("phone", user.getPhone());
        vo.put("nickname", user.getNickname());
        vo.put("avatarUrl", user.getAvatarUrl());
        vo.put("realAuthStatus", user.getRealAuthStatus() != null ? user.getRealAuthStatus() : 0);
        // 实名信息（管理后台可见）
        vo.put("realName", user.getRealName());
        vo.put("idCard", decodeIdCard(user.getIdCard()));
        log.info("[buildUserVO] userId={}, realName={}, idCard={}", 
                user.getId(), user.getRealName(), decodeIdCard(user.getIdCard()));
        vo.put("inviteCode", user.getInviteCode());
        vo.put("status", user.getStatus());
        vo.put("createdAt", user.getCreatedAt());
        // 余额（列表批量填充，详情单独查）
        vo.put("balance", balance != null ? balance.setScale(2, BigDecimal.ROUND_HALF_UP) : null);
        return vo;
    }

    /** 用户详情 VO（返回明文手机号，供编辑弹窗使用） */
    private Map<String, Object> buildUserDetailVO(AppUser user) {
        return buildUserVO(user, null);
    }

    /**
     * 解码身份证号：库中以 [ENCRYPTED]<明文18位> 形式存储，
     * 去除 [ENCRYPTED] 前缀即可得到明文身份证号（占位"加密"，非真 AES）。
     */
    private String decodeIdCard(String idCard) {
        if (idCard == null) return null;
        return idCard.startsWith("[ENCRYPTED]") ? idCard.substring(11) : idCard;
    }

    /**
     * 查询用户当前余额（最新一条收益记录的 balance_after）
     * GET /api/admin/users/{userId}/balance
     */
    @GetMapping("/{userId}/balance")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN', 'FINANCE')")
    public ApiResponse<?> getUserBalance(@PathVariable Long userId) {
        // 校验用户是否存在
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        BigDecimal balance = userEarningsMapper.selectLatestBalance(userId);
        if (balance == null) {
            balance = BigDecimal.ZERO.setScale(2, BigDecimal.ROUND_HALF_UP);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("balance", balance);
        return ApiResponse.success(result);
    }

    /**
     * 查询用户收益流水（分页）
     * GET /api/admin/users/{userId}/earnings?page=1&size=20
     */
    @GetMapping("/{userId}/earnings")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN', 'FINANCE')")
    public ApiResponse<?> getUserEarnings(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        // 校验用户是否存在
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        long offset = (long) (page - 1) * size;
        List<Map<String, Object>> records = userEarningsMapper.selectByUserIdWithPage(userId, offset, size);
        long total = userEarningsMapper.countByUserId(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("records", records);
        return ApiResponse.success(result);
    }
}
