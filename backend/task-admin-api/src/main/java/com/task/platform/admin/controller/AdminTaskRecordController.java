package com.task.platform.admin.controller;

import com.task.platform.admin.mapper.UserTaskRecordMapper;
import com.task.platform.admin.security.AdminUserDetails;
import com.task.platform.admin.service.MerchantService;
import com.task.platform.admin.service.RewardGrantService;
import com.task.platform.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台 - 任务领取记录接口
 */
@Slf4j
@RestController
@RequestMapping("/admin/task-records")
@RequiredArgsConstructor
public class AdminTaskRecordController {

    private final UserTaskRecordMapper userTaskRecordMapper;
    private final MerchantService merchantService;
    private final RewardGrantService rewardGrantService;

    /**
     * 领取记录列表（跨任务，按状态过滤）
     * GET /api/admin/task-records?status=1&page=1&size=20
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> listRecords(
            @AuthenticationPrincipal AdminUserDetails currentUser,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long merchantId = currentUser.isSuperAdmin() ? null : currentUser.getMerchantId();

        List<Map<String, Object>> all = userTaskRecordMapper.selectByStatusWithUserAndTask(status, merchantId);

        long total = all.size();
        int fromIndex = Math.max(0, Math.min((page - 1) * size, all.size()));
        int toIndex   = Math.min(page * size, all.size());
        List<Map<String, Object>> records = all.subList(fromIndex, toIndex);

        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        data.put("records", records);
        return ApiResponse.success(data);
    }

    /**
     * 根据任务ID查询所有领取记录（含用户信息）
     * GET /api/admin/task-records/task/{taskId}?page=1&size=20
     */
    @GetMapping("/task/{taskId}")
    public ApiResponse<Map<String, Object>> getRecordsByTaskId(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        List<Map<String, Object>> all = userTaskRecordMapper.selectByTaskIdWithUser(taskId);

        long total = all.size();
        int fromIndex = Math.min((page - 1) * size, all.size());
        int toIndex   = Math.min(page * size, all.size());
        List<Map<String, Object>> records = all.subList(fromIndex, toIndex);

        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        data.put("records", records);
        return ApiResponse.success(data);
    }

    /**
     * 根据记录ID查询详情（含用户信息 + 任务信息）
     * GET /api/admin/task-records/{recordId}
     */
    @GetMapping("/{recordId}")
    public ApiResponse<Map<String, Object>> getRecordDetail(
            @PathVariable Long recordId) {

        Map<String, Object> detail = userTaskRecordMapper.selectByRecordIdWithUserAndTask(recordId);
        if (detail == null) {
            return ApiResponse.error(404, "记录不存在");
        }
        return ApiResponse.success(detail);
    }

    /**
     * 审核通过：扣商户费（保留）+ 委托 pay-service 发放奖励（唯一权威发奖入口）
     * POST /api/admin/task-records/{recordId}/approve
     *
     * <p>设计：先标记记录为通过（reward_granted_at 留空），再调 pay grant；
     * 若 pay 临时不可达，记录保持 status=2 且 reward_granted_at 为 NULL，
     * 由 RewardGrantCompensationJob 定时补偿重试（幂等），避免双发/双扣。</p>
     */
    @PostMapping("/{recordId}/approve")
    public ApiResponse<Void> approve(@PathVariable Long recordId) {
        Map<String, Object> detail = userTaskRecordMapper.selectByRecordIdWithUserAndTask(recordId);
        if (detail == null) {
            return ApiResponse.error(404, "记录不存在");
        }

        Integer status = detail.get("status") != null ? ((Number) detail.get("status")).intValue() : null;
        Object grantedAtObj = detail.get("rewardGrantedAt");
        boolean alreadyGranted = (status != null && status == 2 && grantedAtObj != null);
        if (alreadyGranted) {
            return ApiResponse.success(null, "已发放，无需重复操作");
        }

        BigDecimal rewardAmount = userTaskRecordMapper.selectRewardAmount(recordId);
        if (rewardAmount == null) {
            return ApiResponse.error(500, "无法获取奖励金额");
        }

        Long userId = detail.get("userId") != null ? ((Number) detail.get("userId")).longValue() : null;
        Long taskId = detail.get("taskId") != null ? ((Number) detail.get("taskId")).longValue() : null;
        Long merchantId = detail.get("merchantId") != null ? ((Number) detail.get("merchantId")).longValue() : null;
        String taskTitle = (String) detail.get("taskTitle");

        // 首次通过（status==1）才扣商户；半处理重试（status==2 但未发奖）跳过，避免双扣
        boolean firstAttempt = (status == null || status == 1);
        if (firstAttempt && merchantId != null && rewardAmount.compareTo(BigDecimal.ZERO) > 0) {
            merchantService.deductTaskCost(merchantId, rewardAmount, taskId, taskTitle);
        }

        // 先标记记录为通过（reward_granted_at 留空），失败可由补偿任务重试
        userTaskRecordMapper.approve(recordId, rewardAmount);

        // 委托 pay-service 发放用户奖励（幂等，唯一权威发奖入口）
        rewardGrantService.grant(userId, recordId, taskId, rewardAmount);

        // 发放成功，写发放时间
        userTaskRecordMapper.markGranted(recordId);

        return ApiResponse.success(null);
    }

    /**
     * 审核拒绝：将记录状态回退为进行中，用户可重新提交
     * POST /api/admin/task-records/{recordId}/reject?reason=...
     */
    @PostMapping("/{recordId}/reject")
    public ApiResponse<Void> reject(
            @PathVariable Long recordId,
            @RequestParam String reason) {

        if (reason == null || reason.isBlank()) {
            return ApiResponse.error(400, "拒绝原因不能为空");
        }
        userTaskRecordMapper.reject(recordId, reason);
        return ApiResponse.success(null);
    }
}
