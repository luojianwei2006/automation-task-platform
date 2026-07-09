package com.task.platform.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.task.platform.admin.dto.publish.CreatePublishTaskReq;
import com.task.platform.admin.dto.publish.UpdatePublishTaskReq;
import com.task.platform.admin.entity.Merchant;
import com.task.platform.admin.entity.PublishMaterial;
import com.task.platform.admin.entity.PublishProject;
import com.task.platform.admin.entity.PublishTask;
import com.task.platform.admin.mapper.MerchantMapper;
import com.task.platform.admin.mapper.PublishMaterialMapper;
import com.task.platform.admin.mapper.PublishProjectMapper;
import com.task.platform.admin.mapper.PublishTaskMapper;
import com.task.platform.admin.util.FeeCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final MerchantMapper merchantMapper;

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

        // 总配额校验（必填，≥1，默认 1）
        int totalQuota = 1;
        if (req.getTotalQuota() != null) {
            if (req.getTotalQuota() < 1) {
                throw new IllegalArgumentException("总配额必须 ≥ 1");
            }
            totalQuota = req.getTotalQuota();
        }
        task.setTotalQuota(totalQuota);
        task.setUsedQuota(0);

        // 预算双算：后端用解析出的商户费率权威重算 budget_points 落库（前端提交仅作参考）
        BigDecimal reward = req.getRewardAmount() != null ? req.getRewardAmount() : BigDecimal.ZERO;
        BigDecimal feeRate = resolveFeeRate(project.getId());
        task.setBudgetPoints(FeeCalculator.computeBudget(reward, feeRate, totalQuota));
        task.setUsedPoints(BigDecimal.ZERO);

        publishTaskMapper.insert(task);
        log.info("[PublishTask] 创建发布任务: id={}, projectId={}, platforms={}, totalQuota={}, budgetPoints={}",
                task.getId(), req.getProjectId(), req.getPlatforms(), totalQuota, task.getBudgetPoints());
        return task;
    }

    /**
     * 任务列表（分页 + 状态筛选）
     */
    public IPage<PublishTask> list(int page, int size, String status) {
        return list(page, size, status, null);
    }

    public IPage<PublishTask> list(int page, int size, String status, Long merchantId) {
        LambdaQueryWrapper<PublishTask> wrapper = new LambdaQueryWrapper<PublishTask>()
                .orderByDesc(PublishTask::getCreatedAt);

        if (status != null && !status.isBlank()) {
            wrapper.eq(PublishTask::getStatus, status.trim());
        }
        // 商户过滤：通过 task → project → merchantId
        if (merchantId != null && merchantId > 0) {
            wrapper.inSql(PublishTask::getProjectId,
                "SELECT id FROM t_project WHERE merchant_id = " + merchantId);
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

        // 总配额仅当显式提供时调整（且须 ≥1）；用于重算预算
        if (req.getTotalQuota() != null) {
            if (req.getTotalQuota() < 1) {
                throw new IllegalArgumentException("总配额必须 ≥ 1");
            }
            task.setTotalQuota(req.getTotalQuota());
        }

        // 仅当奖励或总配额变动时，用商户费率权威重算预算落库
        if (req.getRewardAmount() != null || req.getTotalQuota() != null) {
            BigDecimal reward = task.getRewardAmount() != null ? task.getRewardAmount() : BigDecimal.ZERO;
            BigDecimal feeRate = resolveFeeRate(task.getProjectId());
            task.setBudgetPoints(FeeCalculator.computeBudget(reward, feeRate, task.getTotalQuota()));
        }

        publishTaskMapper.updateById(task);
        log.info("[PublishTask] 更新任务: id={}, rewardAmount={}, totalQuota={}, budgetPoints={}",
                id, task.getRewardAmount(), task.getTotalQuota(), task.getBudgetPoints());
        return task;
    }

    /**
     * 解析任务所属商户的服务费率：
     * task → project → merchant.service_fee_rate（默认 0.15）。
     * 平台任务（project.merchantId 为空）或商户不存在时返回 null（由 FeeCalculator 兜底为默认费率）。
     */
    private BigDecimal resolveFeeRate(Long projectId) {
        if (projectId == null) {
            return null;
        }
        PublishProject project = publishProjectMapper.selectById(projectId);
        if (project == null || project.getMerchantId() == null) {
            return null;
        }
        Merchant merchant = merchantMapper.selectById(project.getMerchantId());
        if (merchant == null) {
            return null;
        }
        return merchant.getServiceFeeRate();
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
