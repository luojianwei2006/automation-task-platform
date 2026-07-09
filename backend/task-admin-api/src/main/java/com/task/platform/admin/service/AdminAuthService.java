package com.task.platform.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.task.platform.admin.entity.AdminUser;
import com.task.platform.admin.mapper.AdminUserMapper;
import com.task.platform.admin.security.AdminUserDetails;
import com.task.platform.common.exception.BusinessException;
import com.task.platform.common.response.ErrorCode;
import com.task.platform.common.utils.JwtUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理员账号服务 - 登录、子账号管理
 *
 * @author TaskPlatform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminUserMapper adminUserMapper;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;

    // 角色类型常量
    public static final int ROLE_SUPER_ADMIN        = 1;
    public static final int ROLE_MERCHANT_ADMIN     = 2;
    public static final int ROLE_MERCHANT_OPERATOR  = 3;
    public static final int ROLE_FINANCE            = 4;

    // ==================== 登录 ====================

    /**
     * 管理员登录
     *
     * @param username 账号（手机号）
     * @param password 密码
     * @return JWT Token + 管理员信息
     */
    public Map<String, Object> login(String username, String password) {
        log.info("[DEBUG] login attempt: username={}", username);
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );

            AdminUserDetails userDetails = (AdminUserDetails) authentication.getPrincipal();
            AdminUser adminUser = userDetails.getAdminUser();

            log.info("[DEBUG] login success: id={}, username={}, roleType={}", 
                adminUser.getId(), adminUser.getUsername(), adminUser.getRoleType());

            // 更新最后登录时间
            adminUser.setLastLoginAt(LocalDateTime.now());
            adminUserMapper.updateById(adminUser);

            // 生成Token（存入adminId + roleType）
            // 注意：此处用adminId.toString()作为subject，JwtUtil需支持此约定
            String token = JwtUtil.generateToken(adminUser.getId(), getRoleName(adminUser.getRoleType()));
            String refreshToken = JwtUtil.generateRefreshToken(adminUser.getId());

            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("refreshToken", refreshToken);
            response.put("expiresIn", 8 * 60 * 60); // 管理后台8小时过期

            Map<String, Object> adminInfo = new HashMap<>();
            adminInfo.put("id", adminUser.getId());
            adminInfo.put("username", adminUser.getUsername());
            adminInfo.put("displayName", adminUser.getDisplayName());
            adminInfo.put("roleType", adminUser.getRoleType());
            adminInfo.put("roleName", getRoleDisplayName(adminUser.getRoleType()));
            adminInfo.put("merchantId", adminUser.getMerchantId());
            response.put("adminInfo", adminInfo);

            log.info("[AdminAuth] 管理员登录成功: {}, 角色: {}", username, adminUser.getRoleType());
            return response;

        } catch (BadCredentialsException e) {
            log.warn("[DEBUG] login failed: BadCredentials for username={}", username);
            throw new BusinessException(ErrorCode.PASSWORD_ERROR, "账号或密码不正确");
        } catch (DisabledException e) {
            log.warn("[DEBUG] login failed: account disabled, username={}", username);
            throw new BusinessException(ErrorCode.USER_DISABLED, "账号已被禁用");
        }
    }

    /**
     * 修改密码
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long adminId, String oldPassword, String newPassword) {
        log.info("[DEBUG] changePassword: adminId={}", adminId);
        AdminUser admin = adminUserMapper.selectById(adminId);
        if (admin == null) {
            log.warn("[DEBUG] changePassword: admin not found, id={}", adminId);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "账号不存在");
        }
        if (!passwordEncoder.matches(oldPassword, admin.getPassword())) {
            log.warn("[DEBUG] changePassword: old password mismatch for adminId={}", adminId);
            throw new BusinessException(ErrorCode.PASSWORD_ERROR, "旧密码不正确");
        }
        String newHash = passwordEncoder.encode(newPassword);
        admin.setPassword(newHash);
        adminUserMapper.updateById(admin);
        log.info("[DEBUG] changePassword: SUCCESS for adminId={}, newHash={}", adminId, newHash.substring(0, 20) + "...");
    }

    // ==================== 子账号管理 ====================

    /**
     * 创建管理员子账号（超管 or 商户管理员可创建）
     *
     * @param operatorId    操作人ID
     * @param operatorRole  操作人角色
     * @param req           创建请求
     */
    @Transactional(rollbackFor = Exception.class)
    public void createSubAccount(Long operatorId, Integer operatorRole, CreateAccountRequest req) {
        // 权限校验：超管可创建任意角色；商户管理员只能创建本商户的操作员/财务
        if (operatorRole == ROLE_MERCHANT_ADMIN) {
            if (req.getRoleType() == ROLE_SUPER_ADMIN || req.getRoleType() == ROLE_MERCHANT_ADMIN) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "商户管理员无权创建此角色");
            }
            // 商户管理员只能创建本商户下的子账号
            AdminUser operator = adminUserMapper.selectById(operatorId);
            req.setMerchantId(operator.getMerchantId());
        }

        // 检查账号是否已存在
        if (adminUserMapper.selectByUsername(req.getUsername()) != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "账号已存在");
        }

        AdminUser newAdmin = new AdminUser();
        newAdmin.setUsername(req.getUsername());
        String encodedPwd = passwordEncoder.encode(req.getPassword());
        newAdmin.setPassword(encodedPwd);
        newAdmin.setDisplayName(req.getDisplayName());
        newAdmin.setRoleType(req.getRoleType());
        newAdmin.setMerchantId(req.getMerchantId());
        newAdmin.setStatus(1);
        newAdmin.setCreatedBy(operatorId);

        adminUserMapper.insert(newAdmin);
        log.info("[AdminAuth] 操作人 {} 创建子账号: {}, 角色: {}, hash={}",
            operatorId, req.getUsername(), req.getRoleType(), encodedPwd.substring(0, 20) + "...");
    }

    /**
     * 禁用/启用子账号
     */
    @Transactional(rollbackFor = Exception.class)
    public void toggleAccountStatus(Long operatorId, Integer operatorRole,
                                    Long targetAdminId, boolean enable) {
        AdminUser target = adminUserMapper.selectById(targetAdminId);
        if (target == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "账号不存在");
        }

        // 商户管理员只能操作本商户账号
        if (operatorRole == ROLE_MERCHANT_ADMIN) {
            AdminUser operator = adminUserMapper.selectById(operatorId);
            if (!operator.getMerchantId().equals(target.getMerchantId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作其他商户的账号");
            }
        }

        target.setStatus(enable ? 1 : 0);
        adminUserMapper.updateById(target);
    }

    /**
     * 查询商户下的所有子账号
     */
    public List<AdminUserVO> listMerchantSubAccounts(Long merchantId) {
        List<AdminUser> list = adminUserMapper.selectList(
                new LambdaQueryWrapper<AdminUser>()
                        .eq(AdminUser::getMerchantId, merchantId)
                        .orderByAsc(AdminUser::getRoleType)
        );
        return list.stream().map(this::buildAdminUserVO).collect(Collectors.toList());
    }

    // ==================== DTO / VO ====================

    @Data
    public static class CreateAccountRequest {
        private String username;
        private String password;
        private String displayName;
        private Integer roleType;
        private Long merchantId; // 商户管理员创建时自动填入
    }

    @Data
    public static class AdminUserVO {
        private Long id;
        private String username;
        private String displayName;
        private Integer roleType;
        private String roleName;
        private Long merchantId;
        private Integer status;
        private LocalDateTime lastLoginAt;
        private LocalDateTime createdAt;
    }

    // ==================== 私有工具 ====================

    private AdminUserVO buildAdminUserVO(AdminUser adminUser) {
        AdminUserVO vo = new AdminUserVO();
        vo.setId(adminUser.getId());
        vo.setUsername(adminUser.getUsername());
        vo.setDisplayName(adminUser.getDisplayName());
        vo.setRoleType(adminUser.getRoleType());
        vo.setRoleName(getRoleDisplayName(adminUser.getRoleType()));
        vo.setMerchantId(adminUser.getMerchantId());
        vo.setStatus(adminUser.getStatus());
        vo.setLastLoginAt(adminUser.getLastLoginAt());
        vo.setCreatedAt(adminUser.getCreatedAt());
        return vo;
    }

    private String getRoleName(Integer roleType) {
        return switch (roleType) {
            case 1 -> "SUPER_ADMIN";
            case 2 -> "MERCHANT_ADMIN";
            case 3 -> "MERCHANT_OPERATOR";
            case 4 -> "FINANCE";
            default -> "UNKNOWN";
        };
    }

    private String getRoleDisplayName(Integer roleType) {
        return switch (roleType) {
            case 1 -> "超级管理员";
            case 2 -> "商户管理员";
            case 3 -> "商户操作员";
            case 4 -> "财务";
            default -> "未知角色";
        };
    }
}
