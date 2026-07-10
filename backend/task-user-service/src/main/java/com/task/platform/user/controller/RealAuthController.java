package com.task.platform.user.controller;

import com.task.platform.common.exception.BusinessException;
import com.task.platform.common.response.ApiResponse;
import com.task.platform.common.response.ErrorCode;
import com.task.platform.common.utils.JwtUtil;
import com.task.platform.user.service.RealAuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 实名认证接口
 *
 * <p>路径对齐安卓端：/user/realname/{upload,submit,status}（网关去前缀后）。
 * 照片上传经本端点转发 upload-service，返回其 accessUrl。</p>
 */
@Slf4j
@RestController
@RequestMapping("/user/realname")
@RequiredArgsConstructor
public class RealAuthController {

    private final RealAuthService realAuthService;

    private static final RestTemplate REST = new RestTemplate();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${upload.api.base-url:http://localhost:8086}")
    private String uploadApiBaseUrl;

    /**
     * 实名照片上传（身份证/人脸），内部转发 upload-service
     * POST /api/user/realname/upload
     */
    @PostMapping("/upload")
    public ApiResponse<UploadResult> upload(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("file") MultipartFile file) {
        Long userId = JwtUtil.getUserId(extractToken(authorization));
        if (file == null || file.isEmpty()) {
            return ApiResponse.error(400, "上传文件不能为空");
        }
        try {
            String url = uploadApiBaseUrl + "/upload/image?type=idcard";
            LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<LinkedMultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = REST.postForEntity(url, entity, String.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                ApiResponse<UploadResult> ar = OBJECT_MAPPER.readValue(resp.getBody(),
                        OBJECT_MAPPER.getTypeFactory().constructParametricType(ApiResponse.class, UploadResult.class));
                if (ar.getCode() == 200 && ar.getData() != null) {
                    return ApiResponse.success(ar.getData());
                }
                return ApiResponse.error(ar.getCode(), ar.getMsg());
            }
            return ApiResponse.error(500, "上传失败");
        } catch (IOException e) {
            log.error("[RealAuthController] 读取上传文件失败 userId={}", userId, e);
            return ApiResponse.error(500, "上传失败");
        } catch (Exception e) {
            log.error("[RealAuthController] 转发实名图片上传失败 userId={}", userId, e);
            return ApiResponse.error(500, "上传失败");
        }
    }

    /**
     * 提交实名认证申请
     * POST /api/user/realname/submit
     */
    @PostMapping("/submit")
    public ApiResponse<Void> submitRealAuth(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody RealAuthRequest req) {
        Long userId = JwtUtil.getUserId(extractToken(authorization));

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
     * GET /api/user/realname/status
     */
    @GetMapping("/status")
    public ApiResponse<RealAuthService.RealAuthStatusVO> getAuthStatus(
            @RequestHeader("Authorization") String authorization) {
        Long userId = JwtUtil.getUserId(extractToken(authorization));
        return ApiResponse.success(realAuthService.getAuthStatus(userId));
    }

    // ==================== 工具 ====================

    private String extractToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        return authorization.substring(7);
    }

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

    /** 上传结果（与 upload-service UploadResult 结构一致） */
    @Data
    public static class UploadResult {
        private String relativePath;
        private String accessUrl;
        private String filename;
        private long size;
    }
}
