package com.task.platform.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.task.platform.common.utils.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * JWT认证过滤器（管理后台）
 * 解析 Authorization: Bearer {token}，验证后注入 SecurityContext
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final AdminUserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // 无Token，直接放行（公开端点由 SecurityConfig 控制）
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            // 验证Token有效性（JwtUtil会抛出异常if无效）
            Long adminId = JwtUtil.getUserId(token);
            String role = JwtUtil.getRole(token);

            // 如果SecurityContext没有认证信息，则设置
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                // 通过username加载用户（实际存的是username not id）
                // 注意：JWT payload中存的是adminId，需要从DB查出username
                // 这里简化处理：直接用adminId字符串作username存入JWT，DB按此查询
                UserDetails userDetails = userDetailsService.loadUserByUsername(adminId.toString());

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

            Map<String, Object> errorBody = Map.of(
                    "code", 401,
                    "msg", "Token无效或已过期，请重新登录",
                    "data", null
            );
            response.getWriter().write(objectMapper.writeValueAsString(errorBody));
        }
    }
}
