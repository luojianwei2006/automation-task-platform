package com.task.platform.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.task.platform.admin.dto.publish.CreatePublishTaskReq;
import com.task.platform.admin.dto.publish.UpdatePublishTaskReq;
import com.task.platform.admin.entity.PublishMaterial;
import com.task.platform.admin.entity.PublishProject;
import com.task.platform.admin.entity.PublishTask;
import com.task.platform.admin.mapper.PublishMaterialMapper;
import com.task.platform.admin.mapper.PublishProjectMapper;
import com.task.platform.admin.mapper.PublishTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发布任务服务（视频发布功能）
 *
 * @author TaskPlatform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublishTaskService {

    private final PublishTaskMapper publishTaskMapper;
    private final PublishProjectMapper publishProjectMapper;
    private final PublishMaterialMapper publishMaterialMapper;

    // 状态常量
    public static final String STATUS_PENDING   = "pending";
    public static final String STATUS_ONLINE    = "online";
    public static final String STATUS_REJECTED  = "rejected";
    public static final String STATUS_OFFLINE   = "offline";
    public static final String STATUS_CLAIMED   = "claimed";
    public static final String STATUS_RUNNING   = "running";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED    = "failed";
    public static final String STATUS_CANCELLED = "cancelled";

    /**
     * 创建发布任务
     */
    @Transactional(rollbackFor = Exception.class)
    public PublishTask create(CreatePublishTaskReq req) {
        // 校验 project_id 存在
        PublishProject project = publishProjectMapper.selectById(req.getProjectId());
        if (project == null || project.getStatus() == 0) {
            throw new IllegalArgumentException("项目不存在或已删除");
        }

        PublishTask task = new PublishTask();
        task.setProjectId(req.getProjectId());
        task.setPlatforms(req.getPlatforms());
        task.setPublishText(req.getPublishText());
        task.setScheduledAt(req.getScheduledAt());
        task.setMaxRetry(req.getMaxRetry() != null ? req.getMaxRetry() : 3);
        task.setRemark(req.getRemark());
        task.setImages(req.getImages());
        task.setRewardAmount(req.getRewardAmount());
        task.setStatus(STATUS_PENDING);

        publishTaskMapper.insert(task);
        log.info("[PublishTask] 创建发布任务: id={}, projectId={}, platforms={}", task.getId(), req.getProjectId(), req.getPlatforms());
        return task;
    }

    /**
     * 任务列表（分页 + 状态筛选）
     */
    public IPage<PublishTask> list(int page, int size, String status) {
        LambdaQueryWrapper<PublishTask> wrapper = new LambdaQueryWrapper<PublishTask>()
                .orderByDesc(PublishTask::getCreatedAt);

        if (status != null && !status.isBlank()) {
            wrapper.eq(PublishTask::getStatus, status.trim());
        }

        return publishTaskMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /**
     * 任务详情（含项目素材）
     */
    public PublishTask getById(Long id) {
        PublishTask task = publishTaskMapper.selectById(id);
        if (task == null) {
            return null;
        }
        return task;
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

    /**
     * 更新任务
     */
    @Transactional(rollbackFor = Exception.class)
    public PublishTask update(Long id, UpdatePublishTaskReq req) {
        PublishTask task = publishTaskMapper.selectById(id);
        if (task == null) {
            return null;
        }

        if (req.getPlatforms() != null) {
            task.setPlatforms(req.getPlatforms());
        }
        if (req.getPublishText() != null) {
            task.setPublishText(req.getPublishText());
        }
        if (req.getScheduledAt() != null) {
            task.setScheduledAt(req.getScheduledAt());
        }
        if (req.getMaxRetry() != null) {
            task.setMaxRetry(req.getMaxRetry());
        }
        if (req.getRemark() != null) {
            task.setRemark(req.getRemark());
        }
        if (req.getRewardAmount() != null) {
            task.setRewardAmount(req.getRewardAmount());
        }
        if (req.getImages() != null) {
            task.setImages(req.getImages());
        }

        publishTaskMapper.updateById(task);
        log.info("[PublishTask] 更新任务: id={}, rewardAmount={}", id, task.getRewardAmount());
        return task;
    }

    /**
     * 审核任务（仅pending状态）
     * 通过 → status=online, publishedAt=now
     * 拒绝 → status=rejected, errorMessage=reason
     */
    @Transactional(rollbackFor = Exception.class)
    public PublishTask review(Long taskId, boolean pass, String reason) {
        PublishTask task = publishTaskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        if (!STATUS_PENDING.equals(task.getStatus())) {
            throw new IllegalStateException("仅pending状态的任务可以审核，当前状态: " + task.getStatus());
        }

        if (pass) {
            task.setStatus(STATUS_ONLINE);
            task.setPublishedAt(LocalDateTime.now());
        } else {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("拒绝时必须填写拒绝原因");
            }
            task.setStatus(STATUS_REJECTED);
            task.setErrorMessage(reason);
        }

        publishTaskMapper.updateById(task);
        log.info("[PublishTask] 审核任务: id={}, pass={}", taskId, pass);
        return task;
    }

    /**
     * 下架任务（仅online状态）
     */
    @Transactional(rollbackFor = Exception.class)
    public PublishTask offline(Long taskId) {
        PublishTask task = publishTaskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        if (!STATUS_ONLINE.equals(task.getStatus())) {
            throw new IllegalStateException("仅online状态的任务可以下架，当前状态: " + task.getStatus());
        }

        task.setStatus(STATUS_OFFLINE);
        publishTaskMapper.updateById(task);
        log.info("[PublishTask] 下架任务: id={}", taskId);
        return task;
    }

    /**
     * 取消任务（仅pending状态）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean cancel(Long id) {
        PublishTask task = publishTaskMapper.selectById(id);
        if (task == null) {
            return false;
        }
        if (!STATUS_PENDING.equals(task.getStatus())) {
            throw new IllegalStateException("仅pending状态的任务可以取消，当前状态: " + task.getStatus());
        }
        task.setStatus(STATUS_CANCELLED);
        publishTaskMapper.updateById(task);
        log.info("[PublishTask] 取消任务: id={}", id);
        return true;
    }
}
