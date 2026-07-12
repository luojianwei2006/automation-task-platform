package com.task.platform.gateway.filter;

import com.task.platform.common.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * JWT 全局鉴权过滤器
 *
 * 白名单配置（无需登录）：
 * - POST /api/user/auth/login（密码登录）
 * - POST /api/user/auth/sms-login（验证码登录）
 * - POST /api/user/auth/register（注册）
 * - POST /api/user/auth/sms/send（发送验证码）
 * - GET /api/admin/auth/login（管理员登录）
 * - GET /api/task/tasks（任务大厅列表 - 仅GET）
 * - GET /api/task/tasks/*（任务详情 - 仅GET）
 */
@Slf4j
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    /**
     * 白名单配置（路径 + HTTP方法）
     * method=null 表示不限制HTTP方法（全放行）
     */
    private static final List<WhiteListEntry> WHITE_LIST = Arrays.asList(
            new WhiteListEntry("/api/user/auth/login", "POST"),
            new WhiteListEntry("/api/user/auth/sms-login", "POST"),
            new WhiteListEntry("/api/user/auth/register", "POST"),
            new WhiteListEntry("/api/user/auth/sms/send", "POST"),
            // C 端 App 版本配置（公开接口，无需登录，供安卓启动后比对更新）
            new WhiteListEntry("/api/user/config", "GET"),
            new WhiteListEntry("/api/admin/auth/login", "POST"),
            new WhiteListEntry("/api/admin/auth/captcha", "GET"),
            // 任务大厅（仅GET请求放行，POST/PUT/DELETE仍需认证）
            new WhiteListEntry("/api/task/tasks", "GET"),
            // 上传文件静态访问（图片等资源加载时浏览器不带 Token）
            new WhiteListEntry("/api/upload/uploads", "GET"),
            // 管理后台静态文件
            new WhiteListEntry("/api/uploads", "GET"),
            // 发布管理后台接口（admin-api 已做 permitAll，Gateway 无需重复鉴权）
            new WhiteListEntry("/api/publish", null),
            // 移动端发布素材预览接口（仅GET，admin-api 已做 permitAll）
            new WhiteListEntry("/api/mobile/publish/materials", "GET")
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().toString();
        String method = request.getMethod().name();

        // 1. 白名单检查
        if (isWhitelisted(path, method)) {
            return chain.filter(exchange);
        }

        // 2. OPTIONS 预检请求放行
        if ("OPTIONS".equals(method)) {
            return chain.filter(exchange);
        }

        // 3. 提取 Token
        String token = extractToken(request);
        if (token == null) {
            return unauthorized(exchange, "未登录，请先登录");
        }

        // 4. 解析并校验 Token
        try {
            Claims claims = JwtUtil.parseToken(token);
            Long userId = Long.valueOf(claims.getSubject());
            String role = claims.get("role", String.class);

            // 将用户信息传递给下游服务
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Id", String.valueOf(userId))
                    .header("X-User-Role", role)
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (Exception e) {
            log.warn("Token 校验失败 path={}, error={}", path, e.getMessage());
            return unauthorized(exchange, "登录已过期，请重新登录");
        }
    }

    /**
     * 判断是否在白名单中
     * @param path 请求路径
     * @param method HTTP方法
     * @return true=放行，false=需要认证
     */
    private boolean isWhitelisted(String path, String method) {
        for (WhiteListEntry entry : WHITE_LIST) {
            if (path.startsWith(entry.getPath())) {
                // 检查HTTP方法是否匹配
                if (entry.getMethod() == null || entry.getMethod().equals(method)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 从请求头提取 Bearer Token
     */
    private String extractToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    /**
     * 返回 401 未授权响应
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().set("Content-Type", "application/json;charset=UTF-8");

        String body = String.format(
                "{\"code\":401,\"message\":\"%s\",\"data\":null}",
                message
        );

        return response.writeWith(
                Mono.just(response.bufferFactory().wrap(body.getBytes()))
        );
    }

    @Override
    public int getOrder() {
        // 高优先级，在路由之前执行
        return -100;
    }

    /**
     * 白名单条目（路径 + HTTP方法）
     */
    @Data
    public static class WhiteListEntry {
        /** 路径前缀 */
        private String path;
        /** HTTP方法（null=不限制） */
        private String method;

        public WhiteListEntry(String path, String method) {
            this.path = path;
            this.method = method;
        }

        public WhiteListEntry(String path) {
            this(path, null);
        }
    }
}
