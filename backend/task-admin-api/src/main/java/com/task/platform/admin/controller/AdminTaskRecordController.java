package com.task.platform.admin.controller;

import com.task.platform.admin.mapper.UserEarningsMapper;
import com.task.platform.admin.mapper.UserTaskRecordMapper;
import com.task.platform.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台 - 任务领取记录接口
 */
@RestController
@RequestMapping("/admin/task-records")
@RequiredArgsConstructor
public class AdminTaskRecordController {

    private final UserTaskRecordMapper userTaskRecordMapper;
    private final UserEarningsMapper userEarningsMapper;

    /**
     * 根据任务ID查询所有领取记录（含用户信息）
     * GET /api/admin/task-records/task/{taskId}?page=1&size=20
     */
    @GetMapping("/task/{taskId}")
    public ApiResponse<Map<String, Object>> getRecordsByTaskId(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        // 先查全部（MyBatis-Plus 的 BaseMapper 不支持联表分页，这里先查全部再手动分页）
        // 数据量不大（一个任务的领取记录不会太多），直接查全量
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
     * 审核通过：给用户发放奖励，更新记录状态为通过
     * POST /api/admin/task-records/{recordId}/approve
     */
    @PostMapping("/{recordId}/approve")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> approve(@PathVariable Long recordId) {
        // 1. 获取奖励金额（COALESCE(r.reward_amount, t.reward_amount)）
        BigDecimal rewardAmount = userTaskRecordMapper.selectRewardAmount(recordId);
        if (rewardAmount == null) {
            return ApiResponse.error(500, "无法获取奖励金额");
        }

        // 2. 获取记录得到 userId
        Map<String, Object> detail = userTaskRecordMapper.selectByRecordIdWithUserAndTask(recordId);
        if (detail == null) {
            return ApiResponse.error(404, "记录不存在");
        }
        Long userId = ((Number) detail.get("userId")).longValue();

        // 3. 获取用户最新余额
        BigDecimal currentBalance = userEarningsMapper.selectLatestBalance(userId);
        if (currentBalance == null) {
            currentBalance = BigDecimal.ZERO;
        }

        // 4. 计算新余额并写入收益明细
        BigDecimal newBalance = currentBalance.add(rewardAmount);
        Map<String, Object> earning = new HashMap<>();
        earning.put("userId", userId);
        earning.put("relatedId", recordId);
        earning.put("type", 1); // 1=任务奖励
        earning.put("amount", rewardAmount);
        earning.put("balanceAfter", newBalance);
        earning.put("remark", "任务审核通过，奖励发放");
        userEarningsMapper.insertEarning(earning);

        // 5. 更新记录状态为通过
        userTaskRecordMapper.approve(recordId, rewardAmount);

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
