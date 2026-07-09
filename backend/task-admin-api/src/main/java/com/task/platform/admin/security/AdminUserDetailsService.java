package com.task.platform.admin.security;

import com.task.platform.admin.entity.AdminUser;
import com.task.platform.admin.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Slf4j

/**
 * Spring Security UserDetailsService 实现
 * 从数据库加载管理员用户
 */
@Service
@RequiredArgsConstructor
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserMapper adminUserMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("[DEBUG] loadUserByUsername: username={}", username);
        AdminUser adminUser = adminUserMapper.selectByUsername(username);
        if (adminUser == null) {
            log.warn("[DEBUG] loadUserByUsername: user NOT FOUND: {}", username);
            throw new UsernameNotFoundException("管理员账号不存在: " + username);
        }
        log.info("[DEBUG] loadUserByUsername: found id={}, status={}, passwordHash={}", 
            adminUser.getId(), adminUser.getStatus(), adminUser.getPassword().substring(0, 20) + "...");
        return new AdminUserDetails(adminUser);
    }

    /**
     * 按 ID 加载用户（JWT 中存的是 adminId）
     */
    public UserDetails loadUserById(Long id) {
        log.info("[DEBUG] loadUserById: id={}", id);
        AdminUser adminUser = adminUserMapper.selectById(id);
        if (adminUser == null) {
            log.warn("[DEBUG] loadUserById: user NOT FOUND: id={}", id);
            throw new UsernameNotFoundException("管理员账号不存在: " + id);
        }
        log.info("[DEBUG] loadUserById: found username={}, status={}", adminUser.getUsername(), adminUser.getStatus());
        return new AdminUserDetails(adminUser);
    }
}
