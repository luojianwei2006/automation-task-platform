package com.task.platform.task.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT Token 解析器
 * 从 task-admin-api 的 JwtTokenProvider 复制，保持一致的 JWT 解析逻辑
 */
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret:defaultSecretKeyWhichShouldBeLongEnoughForHS512}")
    private String secret;

    /**
     * 从 Token 解析用户ID（普通用户）
     */
    public Long getUserId(String token) {
        Claims claims = parseClaims(token);
        return claims.get("userId", Long.class);
    }

    /**
     * 从 Token 解析用户ID（兼容旧Token，如果userId不存在则从subject获取）
     */
    public Long getUserIdFallback(String token) {
        Claims claims = parseClaims(token);
        Long userId = claims.get("userId", Long.class);
        if (userId != null) {
            return userId;
        }
        // 旧Token中只有sub，尝试解析
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从 Token 解析用户ID（管理员）
     */
    public Long getAdminId(String token) {
        Claims claims = parseClaims(token);
        return claims.get("adminId", Long.class);
    }

    /**
     * 从 Token 解析用户名
     */
    public String getUsername(String token) {
        Claims claims = parseClaims(token);
        return claims.getSubject();
    }

    /**
     * 从 Token 解析商户ID
     */
    public Long getMerchantId(String token) {
        Claims claims = parseClaims(token);
        return claims.get("merchantId", Long.class);
    }

    /**
     * 从 Token 解析角色列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        Claims claims = parseClaims(token);
        return claims.get("roles", List.class);
    }

    /**
     * 解析 Claims
     */
    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 验证 Token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 Token 创建 JwtClaims 对象
     * 支持普通用户和管理员
     */
    public JwtClaims getJwtClaims(String token) {
        // 优先解析 userId（普通用户）
        Long userId = getUserId(token);
        // 兼容旧Token，如果userId不存在则从subject获取
        if (userId == null) {
            try {
                userId = Long.valueOf(getUsername(token));
            } catch (NumberFormatException e) {
                userId = null;
            }
        }
        
        Long adminId = getAdminId(token);
        String username = getUsername(token);
        Long merchantId = getMerchantId(token);
        List<String> roles = getRoles(token);

        JwtClaims claims = new JwtClaims(userId, adminId, merchantId, username, roles);
        return claims;
    }
}
