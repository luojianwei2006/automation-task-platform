package com.task.platform.gateway.filter;

import com.task.platform.common.utils.JwtUtil;
import io.jsonwebtoken.Claims;
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

/**
 * JWT 全局鉴权过滤器
 *
 * 白名单路径（无需登录）：
 * - POST /api/user/auth/login（密码登录）
 * - POST /api/user/auth/sms-login（验证码登录）
 * - POST /api/user/auth/register（注册）
 * - POST /api/user/auth/sms-code（发送验证码）
 * - GET /api/admin/auth/login（管理员登录）
 */
@Slf4j
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    /** 需要放行的白名单路径前缀 */
    private static final String[] WHITE_LIST = {
        "/api/user/auth/login",
        "/api/user/auth/sms-login",
        "/api/user/auth/register",
        "/api/user/auth/sms-code",
        "/api/admin/auth/login",
    };

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().toString();

        // 1. 白名单放行
        if (isWhiteListed(path)) {
            return chain.filter(exchange);
        }

        // 2. OPTIONS 预检请求放行
        if (request.getMethod().name().equals("OPTIONS")) {
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
            log.warn("JWT 校验失败: path={}, error={}", path, e.getMessage());
            return unauthorized(exchange, "登录已过期，请重新登录");
        }
    }

    /**
     * 判断是否在白名单中
     */
    private boolean isWhiteListed(String path) {
        for (String prefix : WHITE_LIST) {
            if (path.startsWith(prefix)) {
                return true;
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
}
