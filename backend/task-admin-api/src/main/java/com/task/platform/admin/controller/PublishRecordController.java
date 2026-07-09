package com.task.platform.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.task.platform.admin.dto.publish.PublishRecordVO;
import com.task.platform.admin.entity.AppUser;
import com.task.platform.admin.entity.Merchant;
import com.task.platform.admin.entity.PublishProject;
import com.task.platform.admin.entity.PublishTask;
import com.task.platform.admin.entity.UserPublishRecord;
import com.task.platform.admin.mapper.AppUserMapper;
import com.task.platform.admin.mapper.MerchantMapper;
import com.task.platform.admin.mapper.PublishProjectMapper;
import com.task.platform.admin.mapper.PublishTaskMapper;
import com.task.platform.admin.mapper.UserEarningsMapper;
import com.task.platform.admin.mapper.UserPublishRecordMapper;
import com.task.platform.admin.service.MerchantService;
import com.task.platform.admin.util.FeeCalculator;
import com.task.platform.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理员 - 发布记录管理
 */
@Slf4j
@RestController
@RequestMapping("/publish/records")
@RequiredArgsConstructor
public class PublishRecordController {

    private final UserPublishRecordMapper userPublishRecordMapper;
    private final PublishTaskMapper publishTaskMapper;
    private final AppUserMapper appUserMapper;
    private final PublishProjectMapper publishProjectMapper;
    private final UserEarningsMapper userEarningsMapper;
    private final MerchantService merchantService;
    private final MerchantMapper merchantMapper;

    /** 全部领取/提交记录 */
    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long merchantId) {
        LambdaQueryWrapper<UserPublishRecord> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(UserPublishRecord::getStatus, status);
        }
        // 商户过滤：通过 record → task → project → merchantId
        if (merchantId != null && merchantId > 0) {
            wrapper.inSql(UserPublishRecord::getTaskId,
                "SELECT pt.id FROM t_publish_task pt JOIN t_project p ON pt.project_id = p.id WHERE p.merchant_id = " + merchantId);
        }
        wrapper.orderByDesc(UserPublishRecord::getClaimedAt);
        Page<UserPublishRecord> result = userPublishRecordMapper.selectPage(new Page<>(page, size), wrapper);
        return ApiResponse.success(wrapPage(result));
    }

    /** 待审核列表 */
    @GetMapping("/pending-review")
    public ApiResponse<Map<String, Object>> pendingReview(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<UserPublishRecord> result = userPublishRecordMapper.selectPage(
            new Page<>(page, size),
            new LambdaQueryWrapper<UserPublishRecord>()
                .eq(UserPublishRecord::getStatus, "SUBMITTED")
                .orderByDesc(UserPublishRecord::getSubmittedAt)
        );
        return ApiResponse.success(wrapPage(result));
    }

    /** 审核通过（发放奖励 + 商户扣费） */
    @PostMapping("/{id}/approve")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> approve(@PathVariable Long id) {
        UserPublishRecord record = userPublishRecordMapper.selectById(id);
        if (record == null) return ApiResponse.error(404, "记录不存在");
        if (!"SUBMITTED".equals(record.getStatus())) return ApiResponse.error(400, "当前状态不可审核");

        // 从发布任务获取奖励金额
        PublishTask task = publishTaskMapper.selectById(record.getTaskId());
        if (task == null) return ApiResponse.error(404, "关联任务不存在");
        BigDecimal reward = task.getRewardAmount() != null ? task.getRewardAmount() : BigDecimal.ZERO;

        // 解析商户（task → project → merchant）
        Long merchantId = null;
        String projectName = null;
        if (task.getProjectId() != null) {
            PublishProject project = publishProjectMapper.selectById(task.getProjectId());
            if (project != null) {
                merchantId = project.getMerchantId();
                projectName = project.getName();
            }
        }

        // ① 扣费（先于用户收益发放）：平台任务 merchantId==null 不扣，与普通任务一致
        if (merchantId != null) {
            merchantService.deductTaskCost(merchantId, reward, id, projectName);
        }

        // ② 发放奖励：写入收益流水
        Long userId = record.getUserId();
        BigDecimal currentBalance = userEarningsMapper.selectLatestBalance(userId);
        if (currentBalance == null) currentBalance = BigDecimal.ZERO;
        BigDecimal newBalance = currentBalance.add(reward);

        Map<String, Object> earning = new HashMap<>();
        earning.put("userId", userId);
        earning.put("relatedId", id);
        earning.put("type", 1); // 任务奖励
        earning.put("amount", reward);
        earning.put("balanceAfter", newBalance);
        earning.put("remark", "视频发布任务审核通过，奖励发放");
        userEarningsMapper.insertEarning(earning);

        // ③ 更新记录状态
        record.setStatus("PASSED");
        record.setRewardAmount(reward);
        record.setReviewedAt(LocalDateTime.now());
        userPublishRecordMapper.updateById(record);

        // ④ 累加任务已用配额 / 已消耗预算（逐笔结算）
        int usedQuota = (task.getUsedQuota() != null ? task.getUsedQuota() : 0) + 1;
        task.setUsedQuota(usedQuota);
        BigDecimal cost = FeeCalculator.computeSingleCost(reward, resolveFeeRate(merchantId));
        BigDecimal usedPoints = (task.getUsedPoints() != null ? task.getUsedPoints() : BigDecimal.ZERO).add(cost);
        task.setUsedPoints(usedPoints);
        publishTaskMapper.updateById(task);

        log.info("[ADMIN] approve + deduct + reward: recordId={}, merchantId={}, userId={}, reward={}, cost={}, usedQuota={}",
                id, merchantId, userId, reward, cost, usedQuota);
        return ApiResponse.success(null, "审核通过，奖励已发放");
    }

    /** 审核拒绝 */
    @PostMapping("/{id}/reject")
    public ApiResponse<Void> reject(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        UserPublishRecord record = userPublishRecordMapper.selectById(id);
        if (record == null) return ApiResponse.error(404, "记录不存在");

        String reason = (String) body.getOrDefault("reason", "");
        record.setStatus("REJECTED");
        record.setReviewResult(reason);
        record.setReviewedAt(LocalDateTime.now());
        userPublishRecordMapper.updateById(record);
        log.info("[ADMIN] reject: recordId={}, reason={}", id, reason);
        return ApiResponse.success(null, "已拒绝");
    }

    // ====== helpers ======

    /**
     * 解析商户服务费率（平台任务 merchantId==null 时返回默认 0.15）。
     */
    private BigDecimal resolveFeeRate(Long merchantId) {
        if (merchantId == null) {
            return FeeCalculator.DEFAULT_FEE_RATE;
        }
        Merchant merchant = merchantMapper.selectById(merchantId);
        return merchant != null && merchant.getServiceFeeRate() != null
                ? merchant.getServiceFeeRate() : FeeCalculator.DEFAULT_FEE_RATE;
    }

    private List<PublishRecordVO> toVOList(List<UserPublishRecord> records) {
        if (records.isEmpty()) return java.util.Collections.emptyList();
        // 批量查用户手机号
        var userIds = records.stream().map(UserPublishRecord::getUserId).distinct().toList();
        var users = appUserMapper.selectList(new LambdaQueryWrapper<AppUser>().in(AppUser::getId, userIds));
        var phoneMap = users.stream().collect(Collectors.toMap(AppUser::getId, AppUser::getPhone));
        // 批量查任务名称
        var taskIds = records.stream().map(UserPublishRecord::getTaskId).distinct().toList();
        var tasks = publishTaskMapper.selectList(new LambdaQueryWrapper<PublishTask>().in(PublishTask::getId, taskIds));
        var taskNameMap = new HashMap<Long, String>();
        if (!tasks.isEmpty()) {
            var projectIds = tasks.stream().map(PublishTask::getProjectId).filter(Objects::nonNull).distinct().toList();
            var projects = publishProjectMapper.selectList(new LambdaQueryWrapper<PublishProject>().in(PublishProject::getId, projectIds));
            var projectNameMap = projects.stream().collect(Collectors.toMap(PublishProject::getId, PublishProject::getName));
            for (var t : tasks) {
                taskNameMap.put(t.getId(), projectNameMap.getOrDefault(t.getProjectId(), "项目" + t.getProjectId()));
            }
        }

        return records.stream().map(r -> {
            PublishRecordVO vo = new PublishRecordVO();
            vo.setId(r.getId());
            vo.setUserId(r.getUserId());
            vo.setUserPhone(phoneMap.getOrDefault(r.getUserId(), "-"));
            vo.setTaskId(r.getTaskId());
            vo.setTaskName(taskNameMap.getOrDefault(r.getTaskId(), "任务" + r.getTaskId()));
            vo.setStatus(r.getStatus());
            vo.setScreenshots(r.getScreenshots());
            vo.setMergedVideoUrl(r.getMergedVideoUrl());
            vo.setRewardAmount(r.getRewardAmount());
            vo.setClaimedAt(r.getClaimedAt());
            vo.setSubmittedAt(r.getSubmittedAt());
            vo.setReviewedAt(r.getReviewedAt());
            vo.setReviewResult(r.getReviewResult());
            return vo;
        }).toList();
    }

    private Map<String, Object> wrapPage(Page<UserPublishRecord> page) {
        Map<String, Object> data = new HashMap<>();
        data.put("records", toVOList(page.getRecords()));
        data.put("total", page.getTotal());
        return data;
    }
}
