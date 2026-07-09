package com.task.platform.admin.job;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.task.platform.admin.entity.Task;
import com.task.platform.admin.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 定时任务：自动下架已过截止时间的任务
 * 每分钟执行一次
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskExpireJob {

    // 状态常量（与 AdminTaskController 保持一致）
    private static final int STATUS_ONLINE = 1;
    private static final int STATUS_ENDED  = 3;

    private final TaskMapper taskMapper;

    /**
     * 每分钟执行一次，将所有 deadline < now() 且 status=1 的任务改为 status=3
     */
    @Scheduled(fixedRate = 60_000)
    public void expireTasks() {
        LocalDateTime now = LocalDateTime.now();
        int updated = taskMapper.update(
                null,
                new LambdaUpdateWrapper<Task>()
                        .set(Task::getStatus, STATUS_ENDED)
                        .eq(Task::getStatus, STATUS_ONLINE)
                        .lt(Task::getDeadline, now)
        );
        if (updated > 0) {
            log.info("[TaskExpireJob] 自动下架 {} 个已过截止时间的任务", updated);
        }
    }
}
