package com.task.platform.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.task.platform.common.exception.BusinessException;
import com.task.platform.common.response.ErrorCode;
import com.task.platform.task.entity.Task;
import com.task.platform.task.mapper.TaskMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务服务
 * 任务发布、上下架、列表查询
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskMapper taskMapper;

    // 状态常量
    public static final int STATUS_PENDING = 0;   // 待审核
    public static final int STATUS_ONLINE = 1;     // 已上架
    public static final int STATUS_PAUSED = 2;     // 已暂停
    public static final int STATUS_ENDED  = 3;     // 已结束
    public static final int STATUS_REJECTED = 4;   // 已拒绝

    // 平台常量
    public static final int PLATFORM_DOUYIN = 1;  // 抖音
    public static final int PLATFORM_XIAOHONGSHU = 2; // 小红书

    // 任务类型常量
    public static final int TASK_TYPE_LIKE = 1;   // 点赞
    public static final int TASK_TYPE_COMMENT = 2;  // 评论

    /**
     * 发布任务（商户）
     */
    @Transactional(rollbackFor = Exception.class)
    public Task publishTask(Long merchantId, PublishTaskRequest req) {
        Task task = new Task();
        task.setMerchantId(merchantId);
        task.setTitle(req.getTitle());
        task.setPlatform(req.getPlatform());
        task.setTaskType(req.getTaskType());
        task.setTargetUrl(req.getTargetUrl());
        task.setRequirements(req.getRequirements());
        task.setRequirementImages(req.getRequirementImages());
        task.setRewardAmount(req.getRewardAmount());
        task.setTotalQuota(req.getTotalQuota());
        task.setUsedQuota(0);
        task.setDailyLimit(req.getDailyLimit() != null ? req.getDailyLimit() : 0);
        task.setStatus(STATUS_PENDING); // 待审核
        task.setBudgetPoints(req.getBudgetPoints());
        task.setUsedPoints(BigDecimal.ZERO);
        task.setDeadline(req.getDeadline());

        taskMapper.insert(task);
        return task;
    }

    /**
     * 任务列表（分页 + 筛选）
     * 商户只能看自己的任务，超管看全部
     */
    public Page<Task> listTasks(int page, int size, Long merchantId,
                                Integer status, Integer platform, Integer taskType) {
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<Task>()
                .orderByDesc(Task::getCreatedAt);

        // 商户只能看自己的
        if (merchantId != null) {
            wrapper.eq(Task::getMerchantId, merchantId);
        }
        if (status != null) {
            wrapper.eq(Task::getStatus, status);
        }
        if (platform != null) {
            wrapper.eq(Task::getPlatform, platform);
        }
        if (taskType != null) {
            wrapper.eq(Task::getTaskType, taskType);
        }

        return taskMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /**
     * 任务详情
     */
    public Task getTaskDetail(Long taskId, Long merchantId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        // 商户只能看自己的任务
        if (merchantId != null && !task.getMerchantId().equals(merchantId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看此任务");
        }
        return task;
    }

    /**
     * 上下架任务（商户操作自己的任务）
     */
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long taskId, Long merchantId, boolean online) {
        Task task = getTaskDetail(taskId, merchantId);
        int newStatus = online ? STATUS_ONLINE : STATUS_PAUSED;
        task.setStatus(newStatus);
        if (online && task.getPublishedAt() == null) {
            task.setPublishedAt(LocalDateTime.now());
        }
        taskMapper.updateById(task);
    }

    /**
     * 审核任务（超管操作）
     */
    @Transactional(rollbackFor = Exception.class)
    public void reviewTask(Long taskId, boolean pass, String rejectReason) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        if (pass) {
            task.setStatus(STATUS_ONLINE);
            task.setPublishedAt(LocalDateTime.now());
        } else {
            task.setStatus(STATUS_REJECTED);
            task.setRejectReason(rejectReason);
        }
        taskMapper.updateById(task);
    }

    /**
     * 强制下架（超管操作）
     */
    @Transactional(rollbackFor = Exception.class)
    public void forceOffline(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        task.setStatus(STATUS_PAUSED);
        taskMapper.updateById(task);
    }

    // ==================== DTO ====================

    @Data
    public static class PublishTaskRequest {
        private String title;
        private Integer platform;       // 1抖音 2小红书
        private Integer taskType;        // 1点赞 2评论
        private String targetUrl;
        private String requirements;
        private String requirementImages; // JSON数组字符串
        private BigDecimal rewardAmount;
        private Integer totalQuota;
        private Integer dailyLimit;
        private BigDecimal budgetPoints;
        private LocalDateTime deadline;
    }

    @Data
    public static class TaskVO {
        private Long id;
        private Long merchantId;
        private String title;
        private Integer platform;
        private Integer taskType;
        private String targetUrl;
        private BigDecimal rewardAmount;
        private Integer totalQuota;
        private Integer usedQuota;
        private Integer dailyLimit;
        private Integer status;
        private BigDecimal budgetPoints;
        private BigDecimal usedPoints;
        private LocalDateTime deadline;
        private LocalDateTime publishedAt;
        private LocalDateTime createdAt;
    }
}
