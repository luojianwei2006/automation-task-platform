package com.task.platform.user.security;

import com.task.platform.common.constant.InternalApiConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 内部接口鉴权过滤器
 *
 * <p>仅对 {@code /internal/**} 内部端点校验 {@link InternalApiConstants#HEADER_NAME}，
 * 非法请求直接返回 401；其余业务路径（如 /user/**、/earnings/**）直接放行，
 * 避免影响用户服务的正常请求。user-service 无 Spring Security，本过滤器作为 Servlet 过滤器自动注册。</p>
 */
@Component
public class InternalApiFilter extends OncePerRequestFilter {

    @Value("${internal.api-token:task-internal-2026}")
    private String expectedToken;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 仅对 /internal/** 做内部鉴权，其余路径全部放行
        String path = request.getServletPath();
        return !path.startsWith("/internal");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader(InternalApiConstants.HEADER_NAME);
        if (token == null || token.isEmpty() || !token.equals(expectedToken)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"msg\":\"Unauthorized internal call\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
