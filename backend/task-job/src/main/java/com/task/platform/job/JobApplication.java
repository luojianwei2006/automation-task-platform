package com.task.platform.job;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务启动类
 *
 * 职责：
 * 1. 邀请返佣结算（每日凌晨执行）
 * 2. 提现失败重试（每5分钟扫描）
 * 3. 数据统计报表生成
 * 4. 过期任务自动关闭
 */
@SpringBootApplication
@EnableScheduling
public class JobApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobApplication.class, args);
    }
}
