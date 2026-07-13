package com.task.platform.admin.controller;

import com.task.platform.admin.dto.AgreementSaveReq;
import com.task.platform.admin.service.AgreementService;
import com.task.platform.common.response.ApiResponse;
import com.task.platform.common.vo.AgreementVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 协议文档管理接口（写侧，需 RBAC）
 *
 * <p>路由说明（经网关）：
 * <ul>
 *   <li>GET  /api/admin/agreements?type=about  → 网关 StripPrefix=1 → /admin/agreements</li>
 *   <li>POST /api/admin/agreements             → 同上</li>
 * </ul>
 * 两个端点均要求 SUPER_ADMIN 或 MERCHANT_ADMIN 角色（与 SysConfigController 对齐）。</p>
 *
 * @author TaskPlatform
 */
@Slf4j
@RestController
@RequestMapping("/admin/agreements")
@RequiredArgsConstructor
public class AgreementController {

    private final AgreementService agreementService;

    /**
     * 获取指定类型的当前协议内容（编辑回填）。
     * GET /api/admin/agreements?type=about
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
    public ApiResponse<AgreementVO> getByType(@RequestParam("type") String type) {
        return ApiResponse.success(agreementService.getByType(type));
    }

    /**
     * 保存（upsert）协议内容。
     * POST /api/admin/agreements
     *
     * @param req    请求体 { type, title, contentHtml }
     * @param userId 网关注入的操作人ID（X-User-Id）；缺失时落 "admin"
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
    public ApiResponse<AgreementVO> save(
            @Valid @RequestBody AgreementSaveReq req,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        String operator = (userId != null && !userId.isBlank()) ? userId : "admin";
        AgreementVO vo = agreementService.save(req, operator);
        return ApiResponse.success(vo, "保存成功");
    }
}
