package com.task.platform.user.controller;

import com.task.platform.common.response.ApiResponse;
import com.task.platform.common.utils.JwtUtil;
import com.task.platform.user.entity.WithdrawRecord;
import com.task.platform.user.service.WithdrawService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/user/withdraw")
@RequiredArgsConstructor
public class WithdrawController {

    private final WithdrawService withdrawService;

    /** 申请提现 */
    @PostMapping("/apply")
    public ApiResponse<Void> apply(
            @RequestHeader("Authorization") String authorization,
            @RequestBody ApplyRequest req) {
        Long userId = JwtUtil.getUserId(authorization.replace("Bearer ", ""));
        withdrawService.applyWithdraw(userId, req.getAmount(), req.getMethod(), req.getAccount());
        return ApiResponse.success(null, "提现申请已提交，等待审核");
    }

    /** 提现记录 */
    @GetMapping("/records")
    public ApiResponse<List<WithdrawRecord>> records(
            @RequestHeader("Authorization") String authorization) {
        Long userId = JwtUtil.getUserId(authorization.replace("Bearer ", ""));
        return ApiResponse.success(withdrawService.getRecords(userId));
    }

    @Data
    public static class ApplyRequest {
        private BigDecimal amount;
        private String method;  // wechat / alipay
        private String account;
    }
}
