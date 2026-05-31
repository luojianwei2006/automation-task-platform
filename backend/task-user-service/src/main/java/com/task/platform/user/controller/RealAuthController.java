package com.task.platform.user.controller;

import com.task.platform.common.response.ApiResponse;
import com.task.platform.user.service.RealAuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 实名认证接口
 *
 * @author TaskPlatform
 */
@RestController
@RequestMapping("/user/real-auth")
@RequiredArgsConstructor
public class RealAuthController {

    private final RealAuthService realAuthService;

    /**
     * 提交实名认证申请
     * POST /api/v1/user/real-auth
     *
     * Request Body:
     * {
     *   "realName": "张三",
     *   "idCard": "110101199001010011",
     *   "idCardFrontUrl": "https://cos.xxx.com/xxx.jpg",
     *   "idCardBackUrl": "https://cos.xxx.com/xxx.jpg",
     *   "holdIdCardUrl": "https://cos.xxx.com/xxx.jpg"
     * }
     */
    @PostMapping
    public ApiResponse<Void> submitRealAuth(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody RealAuthRequest req) {

        Long userId = extractUserId(authorization);

        RealAuthService.RealAuthRequest serviceReq = new RealAuthService.RealAuthRequest();
        serviceReq.setRealName(req.getRealName());
        serviceReq.setIdCard(req.getIdCard());
        serviceReq.setIdCardFrontUrl(req.getIdCardFrontUrl());
        serviceReq.setIdCardBackUrl(req.getIdCardBackUrl());
        serviceReq.setHoldIdCardUrl(req.getHoldIdCardUrl());

        realAuthService.submitRealAuth(userId, serviceReq);
        return ApiResponse.success(null, "提交成功，请等待审核");
    }

    /**
     * 查询实名认证状态
     * GET /api/v1/user/real-auth/status
     *
     * Response:
     * {
     *   "status": 1,             // 0未认证 1审核中 2已认证 3失败
     *   "statusDesc": "审核中...",
     *   "realName": "张三",
     *   "idCardMasked": "110101********0011"
     * }
     */
    @GetMapping("/status")
    public ApiResponse<RealAuthService.RealAuthStatusVO> getAuthStatus(
            @RequestHeader("Authorization") String authorization) {

        Long userId = extractUserId(authorization);
        return ApiResponse.success(realAuthService.getAuthStatus(userId));
    }

    // ==================== DTO ====================

    @Data
    public static class RealAuthRequest {

        @NotBlank(message = "真实姓名不能为空")
        @Size(min = 2, max = 64, message = "姓名长度2-64位")
        private String realName;

        @NotBlank(message = "身份证号不能为空")
        @Pattern(
            regexp = "^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$",
            message = "身份证号格式不正确"
        )
        private String idCard;

        @NotBlank(message = "身份证正面照不能为空")
        private String idCardFrontUrl;

        @NotBlank(message = "身份证背面照不能为空")
        private String idCardBackUrl;

        /** 手持身份证照片（可选） */
        private String holdIdCardUrl;
    }

    // ==================== 工具方法 ====================

    private Long extractUserId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new com.task.platform.common.exception.BusinessException(
                    com.task.platform.common.response.ErrorCode.TOKEN_INVALID);
        }
        return com.task.platform.common.utils.JwtUtil.getUserId(authorization.substring(7));
    }
}
