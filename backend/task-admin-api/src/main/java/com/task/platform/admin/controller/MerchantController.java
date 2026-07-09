package com.task.platform.admin.controller;

import com.task.platform.admin.entity.Merchant;
import com.task.platform.admin.security.AdminUserDetails;
import com.task.platform.admin.service.MerchantService;
import com.task.platform.admin.service.MerchantTransactionService;
import com.task.platform.common.exception.BusinessException;
import com.task.platform.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 商户管理接口
 *
 * @author TaskPlatform
 */
@Slf4j
@RestController
@RequestMapping("/admin/merchants")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;
    private final MerchantTransactionService merchantTransactionService;

    @Value("${internal.api-token:}")
    private String internalApiToken;

    /**
     * 分页查询商户列表
     * GET /api/admin/merchants?page=1&size=20&keyword=xxx
     *
     * 权限：超管
     */
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Map<String, Object>> listMerchants(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String keyword) {
        
        var pageResult = merchantService.listMerchants(page, size, keyword);
        
        Map<String, Object> data = Map.of(
                "records", pageResult.getRecords(),
                "total", pageResult.getTotal(),
                "page", page,
                "size", size
        );
        return ApiResponse.success(data);
    }

    /**
     * 获取所有商户列表（用于下拉选择）
     * GET /api/admin/merchants/all
     *
     * 权限：超管
     */
    @GetMapping("/all")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<List<Merchant>> getAllMerchants() {
        return ApiResponse.success(merchantService.getAllMerchants());
    }

    /**
     * 查询商户详情
     * GET /api/admin/merchants/{id}
     *
     * 权限：超管
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Merchant> getMerchantDetail(@PathVariable Long id) {
        return ApiResponse.success(merchantService.getMerchantDetail(id));
    }

    /**
     * 创建商户
     * POST /api/admin/merchants
     *
     * 权限：超管
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Long> createMerchant(@RequestBody MerchantService.CreateMerchantRequest req) {
        Long merchantId = merchantService.createMerchant(req);
        return ApiResponse.success(merchantId, "创建成功");
    }

    /**
     * 更新商户信息
     * PUT /api/admin/merchants/{id}
     *
     * 权限：超管
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> updateMerchant(
            @PathVariable Long id,
            @RequestBody MerchantService.UpdateMerchantRequest req) {
        merchantService.updateMerchant(id, req);
        return ApiResponse.success(null, "更新成功");
    }

    /**
     * 启用/禁用商户
     * PUT /api/admin/merchants/{id}/status?enable=true
     *
     * 权限：超管
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> toggleStatus(
            @PathVariable Long id,
            @RequestParam boolean enable) {
        merchantService.toggleStatus(id, enable);
        return ApiResponse.success(null, enable ? "已启用" : "已禁用");
    }

    /**
     * 删除商户（禁用状态）
     * DELETE /api/admin/merchants/{id}
     *
     * 权限：超管
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> deleteMerchant(@PathVariable Long id) {
        merchantService.deleteMerchant(id);
        return ApiResponse.success(null, "删除成功");
    }

    // ==================== 余额调整 ====================

    /**
     * 调整商户余额（充值/扣费）
     * POST /api/admin/merchants/{id}/balance
     * body: { amount: 100, remark: "手动充值" }
     * amount 正数=充值，负数=扣费
     */
    @PostMapping("/{id}/balance")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> adjustBalance(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        java.math.BigDecimal amount = new java.math.BigDecimal(body.get("amount").toString());
        String remark = (String) body.get("remark");
        merchantService.adjustBalance(id, amount, remark);
        String action = amount.compareTo(java.math.BigDecimal.ZERO) > 0 ? "充值" : "扣费";
        return ApiResponse.success(null, action + "成功");
    }

    /**
     * 内部接口：用户提交审核通过时，由任务服务(task-task-service)调用，扣除商户任务费用(奖励+服务费)。
     * 仅允许携带正确内部令牌的服务间调用，不参与前端角色鉴权。
     * POST /admin/merchants/{merchantId}/task-cost
     * body: { "rewardAmount": 10.00, "taskId": 123, "taskTitle": "任务标题" }
     */
    @PostMapping("/{merchantId}/task-cost")
    public ResponseEntity<ApiResponse<Void>> deductTaskCost(
            @PathVariable Long merchantId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody Map<String, Object> body) {
        if (!internalApiToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(403, "非法内部调用"));
        }
        try {
            java.math.BigDecimal rewardAmount =
                    new java.math.BigDecimal(body.get("rewardAmount").toString());
            Long taskId = body.get("taskId") != null
                    ? Long.valueOf(body.get("taskId").toString()) : null;
            String taskTitle = (String) body.get("taskTitle");
            merchantService.deductTaskCost(merchantId, rewardAmount, taskId, taskTitle);
            return ResponseEntity.ok(ApiResponse.success(null, "扣费成功"));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), e.getMessage()));
        }
    }

    // ==================== 商户流水 ====================

    /**
     * 查询商户流水记录
     * GET /api/admin/merchants/{merchantId}/transactions?page=1&size=20
     * 权限：超管（可查任何商户）、商户管理员（只能查自己）
     */
    @GetMapping("/{merchantId}/transactions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
    public ApiResponse<Map<String, Object>> listTransactions(
            @AuthenticationPrincipal AdminUserDetails currentUser,
            @PathVariable Long merchantId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        // 商户管理员只能查自己的流水
        if (!currentUser.isSuperAdmin() && !currentUser.getMerchantId().equals(merchantId)) {
            return ApiResponse.error(403, "无权查看其他商户的流水");
        }
        var pageResult = merchantTransactionService.listTransactions(merchantId, page, size);
        return ApiResponse.success(Map.of(
            "records", pageResult.getRecords(),
            "total", pageResult.getTotal(),
            "page", page,
            "size", size
        ));
    }
}
