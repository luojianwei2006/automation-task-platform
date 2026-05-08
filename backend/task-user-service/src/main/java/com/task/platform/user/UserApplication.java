package com.task.platform.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 用户服务启动类
 * 端口：8081
 */
@MapperScan("com.task.platform.user.mapper")
@SpringBootApplication(scanBasePackages = {"com.task.platform.user", "com.task.platform.common"})
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
