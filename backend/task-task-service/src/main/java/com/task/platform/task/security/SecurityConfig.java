package com.task.platform.task.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（使用 JWT）
            .csrf(csrf -> csrf.disable())
            
            // 无状态 Session
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // 授权规则
            // 注意：Gateway 已 StripPrefix=1 去掉 /api 前缀
            // 所以到达 task-service 的路径是 /task/** 而不是 /api/task/**
            // 公开接口：任务大厅列表、任务详情
            // 需认证接口：接受任务、提交截图、我的任务记录
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/task/tasks/records").authenticated()
                .requestMatchers("/task/tasks/*/accept").authenticated()
                .requestMatchers("/task/tasks/*/submit").authenticated()
                .anyRequest().permitAll()
            )
            
            // 添加 JWT 过滤器
            .addFilterBefore(jwtAuthFilter, 
                            UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
