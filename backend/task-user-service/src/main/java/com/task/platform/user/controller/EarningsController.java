package com.task.platform.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.task.platform.common.response.ApiResponse;
import com.task.platform.user.service.EarningsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * C端收益中心接口
 */
@RestController
@RequestMapping("/user/earnings")
@RequiredArgsConstructor
public class EarningsController {

    private final EarningsService earningsService;

    /**
     * 收益概览
     * GET /user/earnings/summary
     */
    @GetMapping("/summary")
    public ApiResponse<EarningsService.EarningsSummaryVO> getSummary(
            @RequestHeader("Authorization") String authorization) {

        Long userId = extractUserId(authorization);
        EarningsService.EarningsSummaryVO summary = earningsService.getSummary(userId);
        return ApiResponse.success(summary);
    }

    /**
     * 收益明细记录（分页）
     * GET /user/earnings/records?type=1&page=1&size=20
     *
     * @param type 筛选类型（null=全部, 1=任务奖励, 2=广告奖励, 3=邀请返佣, 4=新手奖励）
     */
    @GetMapping("/records")
    public ApiResponse<Page<EarningsService.EarningsRecordVO>> getRecords(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) Integer type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long userId = extractUserId(authorization);
        Page<EarningsService.EarningsRecordVO> result = earningsService.getRecords(userId, type, page, size);
        return ApiResponse.success(result);
    }

    // ─── Token 解析（与 UserController 保持一致） ───

    private Long extractUserId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new com.task.platform.common.exception.BusinessException(
                    com.task.platform.common.response.ErrorCode.TOKEN_INVALID);
        }
        String token = authorization.substring(7);
        return com.task.platform.common.utils.JwtUtil.getUserId(token);
    }
}
