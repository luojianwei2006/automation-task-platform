package com.task.platform.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.task.platform.admin.entity.PublishMaterial;
import com.task.platform.admin.entity.PublishTask;
import com.task.platform.admin.entity.UserPublishRecord;
import com.task.platform.admin.mapper.PublishMaterialMapper;
import com.task.platform.admin.mapper.PublishTaskMapper;
import com.task.platform.admin.mapper.UserPublishRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 移动端发布服务（视频发布功能）
 *
 * @author TaskPlatform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MobilePublishService {

    private final PublishTaskMapper publishTaskMapper;
    private final PublishMaterialMapper publishMaterialMapper;
    private final UserPublishRecordMapper userPublishRecordMapper;

    /**
     * 可领取任务列表（online + 当前用户已claimed）
     */
    public List<PublishTask> getAvailableTasks(Long userId) {
        // 显示所有任务：online + pending + 当前用户已领取的
        List<Long> userClaimedIds = userPublishRecordMapper.selectList(
            new LambdaQueryWrapper<UserPublishRecord>()
                .eq(UserPublishRecord::getUserId, userId)
        ).stream().map(UserPublishRecord::getTaskId).collect(java.util.stream.Collectors.toList());

        LambdaQueryWrapper<PublishTask> wrapper = new LambdaQueryWrapper<PublishTask>();
        // online + pending 状态的任务，以及当前用户已领取的其他状态任务
        wrapper.and(w -> {
            w.in(PublishTask::getStatus, "online", "pending");
            if (!userClaimedIds.isEmpty()) {
                w.or().in(PublishTask::getId, userClaimedIds);
            }
        });
        wrapper.orderByDesc(PublishTask::getCreatedAt);
        return publishTaskMapper.selectList(wrapper);
    }

    /**
     * 领取任务（检查 status=online）
     */
    @Transactional(rollbackFor = Exception.class)
    public PublishTask claim(Long taskId, Long userId) {
        PublishTask task = publishTaskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        if (!PublishTaskService.STATUS_ONLINE.equals(task.getStatus())) {
            throw new IllegalStateException("任务已被领取或不可领取，当前状态: " + task.getStatus());
        }

        task.setStatus(PublishTaskService.STATUS_CLAIMED);
        task.setClaimedBy(userId);
        task.setClaimedAt(LocalDateTime.now());
        publishTaskMapper.updateById(task);

        log.info("[MobilePublish] 领取任务: taskId={}, userId={}", taskId, userId);
        return task;
    }

    /**
     * 我的任务（当前用户已领取或正在执行的任务）
     */
    public List<PublishTask> getMyTasks(Long userId) {
        // 从 t_user_publish_record 查询用户领取的任务ID
        List<UserPublishRecord> records = userPublishRecordMapper.selectList(
            new LambdaQueryWrapper<UserPublishRecord>()
                .eq(UserPublishRecord::getUserId, userId)
        );
        if (records.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<Long> taskIds = records.stream().map(UserPublishRecord::getTaskId).collect(java.util.stream.Collectors.toList());
        return publishTaskMapper.selectList(
            new LambdaQueryWrapper<PublishTask>()
                .in(PublishTask::getId, taskIds)
                .orderByDesc(PublishTask::getId)
        );
    }

    /**
     * 完成任务上报
     */
    @Transactional(rollbackFor = Exception.class)
    public PublishTask complete(Long taskId, Long userId, String resultMessage) {
        PublishTask task = publishTaskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        if (!userId.equals(task.getClaimedBy())) {
            throw new IllegalStateException("只能完成自己领取的任务");
        }
        if (!PublishTaskService.STATUS_CLAIMED.equals(task.getStatus())
                && !PublishTaskService.STATUS_RUNNING.equals(task.getStatus())) {
            throw new IllegalStateException("当前状态不可完成，状态: " + task.getStatus());
        }

        task.setStatus(PublishTaskService.STATUS_COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        if (resultMessage != null) {
            task.setRemark(resultMessage);
        }
        publishTaskMapper.updateById(task);

        log.info("[MobilePublish] 完成上报: taskId={}, userId={}", taskId, userId);
        return task;
    }

    /**
     * 获取任务详情（移动端）
     */
    public PublishTask getTaskById(Long taskId) {
        return publishTaskMapper.selectById(taskId);
    }

    /**
     * 获取任务关联的素材列表
     */
    public List<PublishMaterial> getTaskMaterials(Long taskId) {
        PublishTask task = publishTaskMapper.selectById(taskId);
        if (task == null) {
            return List.of();
        }
        return publishMaterialMapper.selectList(
                new LambdaQueryWrapper<PublishMaterial>()
                        .eq(PublishMaterial::getProjectId, task.getProjectId())
                        .eq(PublishMaterial::getDeleted, 0)
                        .orderByAsc(PublishMaterial::getSortOrder)
        );
    }

    public void updateTask(PublishTask task) {
        publishTaskMapper.updateById(task);
    }
}
