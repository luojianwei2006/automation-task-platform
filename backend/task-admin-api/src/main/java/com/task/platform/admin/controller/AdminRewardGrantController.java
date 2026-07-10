package com.task.platform.admin.controller;

import com.task.platform.admin.mapper.RewardGrantMapper;
import com.task.platform.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台 - 奖励发放记录接口（对账）
 * GET /api/admin/reward-grants
 */
@RestController
@RequestMapping("/admin/reward-grants")
@RequiredArgsConstructor
public class AdminRewardGrantController {

    private final RewardGrantMapper rewardGrantMapper;

    /**
     * 奖励发放记录列表（关联用户、任务）
     * GET /api/admin/reward-grants?userId=&status=&page=1&size=20
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'FINANCE')")
    public ApiResponse<?> list(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        long offset = (long) (page - 1) * size;
        List<Map<String, Object>> records = rewardGrantMapper.selectListWithUserAndTask(userId, status, offset, size);
        long total = rewardGrantMapper.count(userId, status);

        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        data.put("records", records);
        return ApiResponse.success(data);
    }
}
