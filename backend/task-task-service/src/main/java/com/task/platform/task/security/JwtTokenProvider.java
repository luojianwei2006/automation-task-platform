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
     * 从 Token 解析用户ID
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
     */
    public JwtClaims getJwtClaims(String token) {
        Long adminId = getAdminId(token);
        String username = getUsername(token);
        Long merchantId = getMerchantId(token);
        List<String> roles = getRoles(token);

        return new JwtClaims(adminId, merchantId, username, roles);
    }
}
