package com.task.platform.task;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 任务服务启动类
 *
 * 职责：
 * 1. 任务发布/上架/下架
 * 2. 用户接单/提交任务
 * 3. 截图审核（AI视觉 + 人工兜底）
 * 4. 奖励发放（同步/异步）
 */
@SpringBootApplication(scanBasePackages = "com.task.platform")
@EnableScheduling
public class TaskServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskServiceApplication.class, args);
    }
}
