package com.task.platform.task.schedule;

import com.task.platform.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 任务定时任务
 * 处理超时任务等
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TaskSchedule {

    private final TaskService taskService;

    /**
     * 处理超时任务
     * 每分钟执行一次
     */
    @Scheduled(cron = "0 * * * * ?")
    public void processTimeoutTasks() {
        try {
            log.info("开始处理超时任务...");
            int count = taskService.processTimeoutTasks();
            if (count > 0) {
                log.info("处理超时任务完成，共处理 {} 条记录", count);
            }
        } catch (Exception e) {
            log.error("处理超时任务失败", e);
        }
    }
}
