package com.task.platform.admin.config;

import com.task.platform.admin.security.AdminUserDetailsService;
import com.task.platform.admin.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置 - 管理后台
 *
 * RBAC角色访问控制规则：
 * - /api/admin/auth/** : 公开（登录接口）
 * - /api/admin/super/** : 仅 ROLE_SUPER_ADMIN
 * - /api/admin/merchant/** : ROLE_SUPER_ADMIN + ROLE_MERCHANT_ADMIN
 * - /api/admin/finance/** : ROLE_SUPER_ADMIN + ROLE_FINANCE
 * - /api/admin/** : 全部已登录管理员
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true) // 支持 @PreAuthorize 注解
@RequiredArgsConstructor
public class AdminSecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final AdminUserDetailsService adminUserDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（纯API服务，前端SPA不需要）
            .csrf(AbstractHttpConfigurer::disable)

            // 无状态Session（使用JWT，不使用Session）
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // 路由权限规则
            .authorizeHttpRequests(auth -> auth
                // 登录接口公开
                .requestMatchers("/api/admin/auth/login").permitAll()
                // 上传文件公开访问（无需认证）
                .requestMatchers("/uploads/**").permitAll()
                // Actuator健康检查（可选）
                .requestMatchers("/actuator/health").permitAll()
                // Swagger（开发环境，生产需关闭）
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                // 超管专属接口
                .requestMatchers("/api/admin/super/**").hasRole("SUPER_ADMIN")

                // 财务接口：超管 + 财务角色
                .requestMatchers("/api/admin/finance/**")
                    .hasAnyRole("SUPER_ADMIN", "FINANCE")

                // 商户管理接口：超管 + 商户管理员
                .requestMatchers("/api/admin/merchant/**")
                    .hasAnyRole("SUPER_ADMIN", "MERCHANT_ADMIN")

                // 其他接口：所有已认证管理员
                .anyRequest().authenticated()
            )

            // 注册JWT过滤器（在UsernamePasswordAuthenticationFilter之前）
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

            // 自定义401/403响应
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"msg\":\"请先登录\",\"data\":null}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":403,\"msg\":\"权限不足\",\"data\":null}");
                })
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
