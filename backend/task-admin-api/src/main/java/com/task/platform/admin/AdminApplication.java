package com.task.platform.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 管理后台API启动类
 * 端口：8084
 */
@SpringBootApplication(scanBasePackages = {
    "com.task.platform.admin",
    "com.task.platform.common"
})
@MapperScan("com.task.platform.admin.mapper")
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
