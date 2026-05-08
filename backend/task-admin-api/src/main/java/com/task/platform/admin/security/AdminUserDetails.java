package com.task.platform.admin.security;

import com.task.platform.admin.entity.AdminUser;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security 管理员用户Principal
 * 封装 AdminUser + 角色权限
 */
@Getter
public class AdminUserDetails implements UserDetails {

    private final AdminUser adminUser;
    private final List<GrantedAuthority> authorities;

    public AdminUserDetails(AdminUser adminUser) {
        this.adminUser = adminUser;
        // 角色名映射
        String roleName = switch (adminUser.getRoleType()) {
            case 1 -> "ROLE_SUPER_ADMIN";
            case 2 -> "ROLE_MERCHANT_ADMIN";
            case 3 -> "ROLE_MERCHANT_OPERATOR";
            case 4 -> "ROLE_FINANCE";
            default -> "ROLE_UNKNOWN";
        };
        this.authorities = List.of(new SimpleGrantedAuthority(roleName));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return adminUser.getPassword();
    }

    @Override
    public String getUsername() {
        return adminUser.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return adminUser.getStatus() == 1;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return adminUser.getStatus() == 1;
    }

    /** 便捷方法：获取管理员ID */
    public Long getAdminId() {
        return adminUser.getId();
    }

    /** 便捷方法：获取关联商户ID */
    public Long getMerchantId() {
        return adminUser.getMerchantId();
    }

    /** 便捷方法：是否超级管理员 */
    public boolean isSuperAdmin() {
        return adminUser.getRoleType() == 1;
    }

    /** 便捷方法：是否商户角色 */
    public boolean isMerchantRole() {
        return adminUser.getRoleType() != null && adminUser.getRoleType() >= 2;
    }
}
