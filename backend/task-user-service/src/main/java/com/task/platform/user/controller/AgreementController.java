package com.task.platform.user.controller;

import com.task.platform.common.response.ApiResponse;
import com.task.platform.common.vo.AgreementVO;
import com.task.platform.user.service.AgreementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 协议文档公开读接口（匿名，无需登录）
 *
 * <p>路由说明（经网关）：
 *   GET /api/user/agreements/{type}
 *     → Gateway StripPrefix=1 → /user/agreements/{type}
 *     → 白名单 {@code /api/user/agreements} (GET) 放行，无需 Token。
 *
 * <p>type 必须 ∈ {about, privacy, register}；合法但无数据时返回 contentHtml="" 的友好空态。</p>
 *
 * @author TaskPlatform
 */
@Slf4j
@RestController
@RequestMapping("/user/agreements")
@RequiredArgsConstructor
public class AgreementController {

    private final AgreementService agreementService;

    /**
     * 获取协议文档
     * GET /api/user/agreements/{type}
     *
     * @param type 协议类型（about / privacy / register）
     */
    @GetMapping("/{type}")
    public ApiResponse<AgreementVO> getByType(@PathVariable("type") String type) {
        if (!agreementService.isValidType(type)) {
            return ApiResponse.error(400, "不支持的协议类型");
        }
        return ApiResponse.success(agreementService.getByType(type));
    }
}
