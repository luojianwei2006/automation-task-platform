package com.task.platform.task.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT 认证用户信息
 * 从 JWT Token 中解析出的用户声明信息
 * 支持：超级管理员、商户管理员、普通用户
 */
public class JwtClaims implements UserDetails {

    private Long userId;      // 普通用户ID（C端用户）
    private Long adminId;     // 管理员ID（管理后台）
    private Long merchantId;  // 商户ID
    private String username;
    private List<String> roles;

    public JwtClaims() {
    }

    public JwtClaims(Long userId, Long adminId, Long merchantId, String username, List<String> roles) {
        this.userId = userId;
        this.adminId = adminId;
        this.merchantId = merchantId;
        this.username = username;
        this.roles = roles;
    }

    // ========== UserDetails 接口方法 ==========

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
    }

    @Override
    public String getPassword() {
        return null; // JWT 认证不需要密码
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    // ========== 业务方法 ==========

    public boolean isSuperAdmin() {
        return roles != null && roles.contains("SUPER_ADMIN");
    }

    public boolean isUser() {
        return roles != null && roles.contains("USER");
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getAdminId() {
        return adminId;
    }

    public void setAdminId(Long adminId) {
        this.adminId = adminId;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
}
