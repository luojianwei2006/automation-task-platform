package com.task.platform.pay.controller;

import com.task.platform.common.constant.InternalApiConstants;
import com.task.platform.common.response.ApiResponse;
import com.task.platform.pay.entity.RewardGrant;
import com.task.platform.pay.service.GrantService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 奖励发放接口（内部权威入口）
 *
 * <p>仅允许内部服务通过 {@link InternalApiConstants#HEADER_NAME} 调用，
 * 由 {@code InternalApiFilter} 前置校验；网关侧 /api/pay/** 还要求 JWT。</p>
 */
@RestController
@RequestMapping("/pay")
@RequiredArgsConstructor
public class GrantController {

    private final GrantService grantService;

    /**
     * 发放任务奖励
     * POST /pay/grant
     */
    @PostMapping("/grant")
    public ApiResponse<GrantResult> grant(
            @RequestHeader(value = InternalApiConstants.HEADER_NAME, required = false) String token,
            @RequestBody GrantRequest req) {
        // token 已由 InternalApiFilter 校验通过；内部调用方保证参数非空
        RewardGrant grant = grantService.grant(
                req.getUserId(), req.getTaskRecordId(), req.getTaskId(), req.getAmount());
        return ApiResponse.success(new GrantResult(grant.getGrantNo(), grant.getStatus()), "发放成功");
    }

    @Data
    public static class GrantRequest {
        private Long userId;
        private Long taskRecordId;
        private Long taskId;
        private BigDecimal amount;
    }

    @Data
    public static class GrantResult {
        private String grantNo;
        private Integer status;

        public GrantResult(String grantNo, Integer status) {
            this.grantNo = grantNo;
            this.status = status;
        }
    }
}
