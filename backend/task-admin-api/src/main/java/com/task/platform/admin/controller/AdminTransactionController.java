package com.task.platform.admin.controller;

import com.task.platform.admin.service.MerchantTransactionService;
import com.task.platform.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理后台 - 全局流水查询接口
 * 超管可查看/筛选所有商户的流水记录
 */
@Slf4j
@RestController
@RequestMapping("/admin/transactions")
@RequiredArgsConstructor
public class AdminTransactionController {

    private final MerchantTransactionService merchantTransactionService;

    /**
     * 全局流水列表（含商户名、筛选条件）
     * GET /api/admin/transactions?merchantId=&type=&startDate=&endDate=&page=1&size=20
     * 权限：超管
     */
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Map<String, Object>> listGlobal(
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        var result = merchantTransactionService.listGlobal(merchantId, type, startDate, endDate, page, size);
        return ApiResponse.success(result);
    }
}
