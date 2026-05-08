package com.task.platform.common.utils;

import cn.hutool.crypto.digest.BCrypt;
import lombok.extern.slf4j.Slf4j;

/**
 * 密码加密工具类
 * 使用BCrypt算法，自动处理盐值
 */
@Slf4j
public class PasswordUtil {

    /**
     * 加密密码
     * @param rawPassword 原始密码
     * @return 加密后的密码（BCrypt哈希）
     */
    public static String encode(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(10));
    }

    /**
     * 验证密码
     * @param rawPassword 原始密码
     * @param encodedPassword 加密后的密码
     * @return 是否匹配
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        try {
            return BCrypt.checkpw(rawPassword, encodedPassword);
        } catch (Exception e) {
            log.error("密码验证失败: {}", e.getMessage());
            return false;
        }
    }

    // 测试用main方法
    public static void main(String[] args) {
        String password = "123456";
        String encoded = encode(password);
        System.out.println("原始密码: " + password);
        System.out.println("加密后: " + encoded);
        System.out.println("验证结果: " + matches(password, encoded));
    }
}
