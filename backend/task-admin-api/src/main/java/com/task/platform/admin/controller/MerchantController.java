package com.task.platform.admin.controller;

import com.task.platform.admin.entity.Merchant;
import com.task.platform.admin.service.MerchantService;
import com.task.platform.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/admin/merchants")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;

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
}
