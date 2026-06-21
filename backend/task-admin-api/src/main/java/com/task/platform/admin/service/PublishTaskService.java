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

        publishTaskMapper.updateById(task);
        log.info("[PublishTask] 更新任务: id={}", id);
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
