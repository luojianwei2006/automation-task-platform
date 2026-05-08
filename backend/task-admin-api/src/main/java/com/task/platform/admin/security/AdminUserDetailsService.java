package com.task.platform.admin.security;

import com.task.platform.admin.entity.AdminUser;
import com.task.platform.admin.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

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
        AdminUser adminUser = adminUserMapper.selectByUsername(username);
        if (adminUser == null) {
            throw new UsernameNotFoundException("管理员账号不存在: " + username);
        }
        return new AdminUserDetails(adminUser);
    }
}
