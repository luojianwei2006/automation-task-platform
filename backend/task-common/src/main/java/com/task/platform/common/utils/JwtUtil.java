package com.task.platform.common.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类（JJWT 0.12+ 写法）
 * 
 * 使用jjwt-api 0.12+版本，需要使用Keys.hmacShaKeyFor()生成密钥
 */
@Slf4j
public class JwtUtil {

    // 密钥（至少32字节，生产环境应从配置中心读取）
    private static final String SECRET = "TaskPlatformSecretKey2026@AutoTask#$%^&*()_+1234567890";

    // Token有效期（30天，单位：毫秒）
    private static final long EXPIRATION = 30 * 24 * 60 * 60 * 1000L;

    // Refresh Token有效期（37天，Token过期后7天内可刷新）
    private static final long REFRESH_EXPIRATION = 37 * 24 * 60 * 60 * 1000L;

    // 生成HMAC密钥
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    /**
     * 生成JWT Token
     * @param userId 用户ID（普通用户或管理员ID）
     * @param role 角色（USER/MERCHANT/ADMIN）
     * @param extraClaims 额外声明（可选）
     * @return Token字符串
     */
    public static String generateToken(Long userId, String role, Map<String, Object> extraClaims) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + EXPIRATION);

        JwtBuilder builder = Jwts.builder()
                .id(String.valueOf(userId))
                .subject(String.valueOf(userId))
                .claim("userId", userId)  // ✅ 添加 userId 声明
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(KEY, Jwts.SIG.HS256);

        if (extraClaims != null) {
            extraClaims.forEach(builder::claim);
        }

        return builder.compact();
    }

    /**
     * 生成JWT Token（无额外参数）
     */
    public static String generateToken(Long userId, String role) {
        return generateToken(userId, role, null);
    }

    /**
     * 生成Refresh Token
     */
    public static String generateRefreshToken(Long userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + REFRESH_EXPIRATION);

        return Jwts.builder()
                .id(String.valueOf(userId))
                .subject(String.valueOf(userId))
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(KEY, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析Token
     * @return Claims对象，解析失败返回null
     */
    public static Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("Token已过期: {}", e.getMessage());
            throw new RuntimeException("Token已过期");
        } catch (JwtException e) {
            log.warn("Token无效: {}", e.getMessage());
            throw new RuntimeException("Token无效");
        }
    }

    /**
     * 获取用户ID
     */
    public static Long getUserId(String token) {
        Claims claims = parseToken(token);
        return Long.valueOf(claims.getSubject());
    }

    /**
     * 获取角色
     */
    public static String getRole(String token) {
        Claims claims = parseToken(token);
        return claims.get("role", String.class);
    }

    /**
     * 检查Token是否过期
     */
    public static boolean isExpired(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (JwtException e) {
            return true;
        }
    }

    /**
     * 是否可以刷新（Token过期不超过7天）
     */
    public static boolean canRefresh(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Date expiration = claims.getExpiration();
            Date now = new Date();
            // 过期不超过7天
            return now.getTime() - expiration.getTime() < 7 * 24 * 60 * 60 * 1000L;
        } catch (ExpiredJwtException e) {
            Date expiration = e.getClaims().getExpiration();
            Date now = new Date();
            return now.getTime() - expiration.getTime() < 7 * 24 * 60 * 60 * 1000L;
        } catch (JwtException e) {
            return false;
        }
    }

    // 测试用main方法
    public static void main(String[] args) {
        Long userId = 12345L;
        String role = "USER";

        // 生成Token
        String token = generateToken(userId, role);
        System.out.println("生成的Token: " + token);

        // 解析Token
        Claims claims = parseToken(token);
        System.out.println("用户ID: " + claims.getSubject());
        System.out.println("角色: " + claims.get("role"));
        System.out.println("过期时间: " + claims.getExpiration());

        // 生成RefreshToken
        String refreshToken = generateRefreshToken(userId);
        System.out.println("RefreshToken: " + refreshToken);
    }
}
