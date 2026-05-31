package com.task.platform.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.task.platform.common.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JWT认证过滤器（管理后台）
 * 解析 Authorization: Bearer {token}，验证后注入 SecurityContext
 *
 * 通过 SecurityConfig 的 addFilterBefore 注册，不添加 @Component
 * 以避免成为全局 Servlet 过滤器导致 shouldNotFilter() 失效
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final AdminUserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    // 公开路径列表（无需 Token 即可访问）
    private static final List<String> PUBLIC_PATHS = List.of(
            "/admin/auth/login",
            "/uploads/"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        
        // 放过 OPTIONS 请求（CORS 预检请求）
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        
        // 放过公开路径
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // 无Token，直接放行（公开端点由 shouldNotFilter 控制，这里只处理需要认证的请求）
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            // 验证Token有效性（JwtUtil 会抛异常 if 无效）
            Long adminId = JwtUtil.getUserId(token);
            String role = JwtUtil.getRole(token);

            // 如果 SecurityContext 没有认证信息，则设置
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                // JWT payload 中存的是 adminId，按 id 查用户
                UserDetails userDetails = userDetailsService.loadUserById(adminId);
                if (userDetails == null) {
                    throw new RuntimeException("管理员账号不存在: " + adminId);
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.warn("[JwtAuthFilter] Token验证失败: {}", e.getMessage());
            // 返回401
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());

            // ✅ 使用 HashMap 而非 Map.of()，因为 HashMap 允许 null 值
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("code", 401);
            errorBody.put("msg", "Token无效或已过期，请重新登录");
            errorBody.put("data", null);

            response.getWriter().write(objectMapper.writeValueAsString(errorBody));
        }
    }
}
