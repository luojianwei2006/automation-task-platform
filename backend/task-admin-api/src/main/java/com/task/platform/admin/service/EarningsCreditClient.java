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
 * 内部调用 user-service 入账的客户端（admin-api 侧）。
 *
 * <p>由 {@code AdminTaskRecordController.approve()} 调用，将审核通过的奖励
 * 直接入账到用户虚拟余额（task-user-service 内部接口 {@code POST /internal/earnings/credit}），
 * 替代原 pay-service 自动发奖。幂等由 user-service 侧 {@code t_user_earnings.biz_id} 唯一索引保证。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EarningsCreditClient {

    @Value("${user.api.base-url:http://localhost:8081}")
    private String userApiBaseUrl;

    @Value("${internal.api-token:task-internal-2026}")
    private String internalApiToken;

    private static final RestTemplate REST = new RestTemplate();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 调用 user-service 内部接口入账任务奖励。
     *
     * @param userId       用户ID
     * @param taskRecordId 用户任务记录ID（幂等键）
     * @param taskId       任务ID（可空）
     * @param amount       奖励金额
     * @param type         收益类型（通常 1=任务收益）
     */
    public void credit(Long userId, Long taskRecordId, Long taskId, BigDecimal amount, Integer type) {
        try {
            String url = userApiBaseUrl + "/internal/earnings/credit";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(InternalApiConstants.HEADER_NAME, internalApiToken);
            Map<String, Object> body = new HashMap<>();
            body.put("userId", userId);
            body.put("taskRecordId", taskRecordId);
            body.put("taskId", taskId);
            body.put("amount", amount);
            body.put("type", type);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = REST.postForEntity(url, entity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                String msg = "奖励入账失败";
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
            log.error("[EarningsCreditClient] 调用 user-service 入账失败 taskRecordId={}", taskRecordId, e);
            throw new BusinessException(ErrorCode.GRANT_FAILED, "奖励入账调用失败");
        }
    }
}
