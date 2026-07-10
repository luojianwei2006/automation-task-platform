package com.task.platform.user.controller;

import com.task.platform.common.response.ApiResponse;
import com.task.platform.user.service.EarningsService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 用户服务内部入账接口（仅内部服务调用）
 *
 * <p>仅允许内部服务通过 {@code X-Internal-Token} 调用，
 * 由 {@code InternalApiFilter} 前置校验；不经过网关，外部不可达。</p>
 *
 * <p>将「任务审核通过 → 奖励入账」统一收敛到本接口，
 * 直接给用户虚拟余额（t_user_earnings）入账，真实打款（提现）由后续流程处理。</p>
 */
@RestController
@RequestMapping("/internal/earnings")
@RequiredArgsConstructor
public class InternalEarningsController {

    private final EarningsService earningsService;

    /**
     * 任务奖励入账（幂等）
     * POST /internal/earnings/credit
     *
     * <p>幂等键为 taskRecordId（写入 biz_id），同一记录重复调用仅入账一次。</p>
     */
    @PostMapping("/credit")
    public ApiResponse<EarningsService.CreditResult> credit(@RequestBody CreditRequest req) {
        EarningsService.CreditResult result = earningsService.credit(
                req.getUserId(), req.getTaskRecordId(), req.getTaskId(), req.getAmount(), req.getType());
        return ApiResponse.success(result, result.isIdempotent() ? "已入账（幂等命中）" : "入账成功");
    }

    /** 内部入账请求体 */
    @Data
    public static class CreditRequest {
        /** 用户ID（必填） */
        private Long userId;
        /** 用户任务记录ID（必填，幂等键） */
        private Long taskRecordId;
        /** 任务ID（可空） */
        private Long taskId;
        /** 奖励金额（必填，正数） */
        private BigDecimal amount;
        /** 收益类型（选填，默认 1=任务收益） */
        private Integer type;
    }
}
