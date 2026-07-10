package com.task.platform.admin.service;

import com.task.platform.common.constant.InternalApiConstants;
import com.task.platform.common.exception.BusinessException;
import com.task.platform.common.response.ApiResponse;
import com.task.platform.common.response.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 内部调用 pay-service 发放奖励的客户端（admin-api 侧）。
 * 由 AdminTaskRecordController 与 RewardGrantCompensationJob 共用，避免重复 HTTP 代码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RewardGrantService {

    @Value("${pay.api.base-url:http://localhost:8087}")
    private String payApiBaseUrl;

    @Value("${internal.api-token:task-internal-2026}")
    private String internalApiToken;

    private static final RestTemplate REST = new RestTemplate();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 调用 pay-service /pay/grant 发放奖励。
     * 幂等由 pay-service 侧 t_reward_grant.task_record_id 唯一约束保证。
     */
    public void grant(Long userId, Long taskRecordId, Long taskId, BigDecimal amount) {
        try {
            String url = payApiBaseUrl + "/pay/grant";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(InternalApiConstants.HEADER_NAME, internalApiToken);
            Map<String, Object> body = new HashMap<>();
            body.put("userId", userId);
            body.put("taskRecordId", taskRecordId);
            body.put("taskId", taskId);
            body.put("amount", amount);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = REST.postForEntity(url, entity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                String msg = "发放奖励失败";
                try {
                    if (resp.getBody() != null) {
                        ApiResponse<?> ar = OBJECT_MAPPER.readValue(resp.getBody(), ApiResponse.class);
                        if (ar.getCode() != 200) {
                            msg = ar.getMsg();
                        }
                    }
                } catch (Exception ignore) {
                    // 用默认提示
                }
                throw new BusinessException(ErrorCode.GRANT_FAILED, msg);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[RewardGrantService] 调用 pay-service 发放奖励失败 taskRecordId={}", taskRecordId, e);
            throw new BusinessException(ErrorCode.GRANT_FAILED, "发放奖励调用失败");
        }
    }
}
