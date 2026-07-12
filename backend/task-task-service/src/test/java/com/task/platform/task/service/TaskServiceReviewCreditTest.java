package com.task.platform.task.service;

import com.task.platform.common.exception.BusinessException;
import com.task.platform.common.response.ErrorCode;
import com.task.platform.task.entity.Task;
import com.task.platform.task.entity.UserTaskRecord;
import com.task.platform.task.mapper.TaskMapper;
import com.task.platform.task.mapper.UserTaskRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TaskService#reviewTaskRecord 单元测试（审核通过 → 调 user-service 新接口入账）。
 *
 * <p>纯 Mockito：mock TaskMapper / UserTaskRecordMapper，并通过反射把静态 final 的
 * {@code REST_TEMPLATE} 替换为 mock RestTemplate（不依赖真实 HTTP / DB），验证：
 * ① reviewTaskRecord 改调 user-service 新接口
 *    POST {user.api.base-url}/internal/earnings/credit（URL 含 /internal/earnings/credit、
 *    请求体 type=1、带 X-Internal-Token）—— 替代已删除的 grantUserReward(pay-service)；
 * ② 通过时写 status=2、rewardGrantedAt、累加 used_quota(+1)/used_points(+amount)；
 * ③ user-service 不可达 / 非 2xx → 抛 BusinessException(GRANT_FAILED)，本地不落库（可重试）。</p>
 *
 * <p>说明：REST_TEMPLATE 为 private static final，需用反射注入 mock；这是本仓库测试
 * TaskService 内部 HTTP 调用的标准做法（与现有 TaskServiceSubmitTest 的 Mockito 风格一致）。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskService.reviewTaskRecord 调 user-service 入账 单元测试")
class TaskServiceReviewCreditTest {

    @Mock
    private TaskMapper taskMapper;
    @Mock
    private UserTaskRecordMapper userTaskRecordMapper;

    @InjectMocks
    private TaskService taskService;

    private RestTemplate mockRestTemplate;

    private static final long RECORD_ID = 1L;
    private static final long USER_ID = 10L;
    private static final long TASK_ID = 20L;

    @BeforeEach
    void setUp() throws Exception {
        Task task = new Task();
        task.setId(TASK_ID);
        task.setRewardAmount(new BigDecimal("100.00"));
        task.setUsedQuota(0);
        task.setUsedPoints(BigDecimal.ZERO);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        // 注入 @Value 配置字段（无 Spring 上下文时默认 null）
        setInstanceField(taskService, "userApiBaseUrl", "http://localhost:8081");
        setInstanceField(taskService, "internalApiToken", "task-internal-2026");

        // 把静态 final REST_TEMPLATE 替换为 mock
        mockRestTemplate = mock(RestTemplate.class);
        Field f = TaskService.class.getDeclaredField("REST_TEMPLATE");
        setStaticFinal(f, mockRestTemplate);
    }

    private static void setInstanceField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static void setStaticFinal(Field field, Object value) throws Exception {
        field.setAccessible(true);
        try {
            Field modifiers = Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        } catch (Exception ignored) {
            // 某些 JVM 上无需移除 final 即可 set，忽略
        }
        field.set(null, value);
    }

    private UserTaskRecord pendingRecord() {
        UserTaskRecord r = new UserTaskRecord();
        r.setId(RECORD_ID);
        r.setUserId(USER_ID);
        r.setTaskId(TASK_ID);
        r.setStatus(1); // 待审核
        return r;
    }

    // ======================== ① 调新接口 + 状态/统计更新 ========================

    @Nested
    @DisplayName("审核通过 → 调 user-service 新接口")
    class PassAndCredit {

        @Test
        @DisplayName("调 POST /internal/earnings/credit（type=1、带 token）；并写 status=2/used_quota+1/used_points+amount")
        void callsNewEndpoint_andUpdatesState() throws Exception {
            when(userTaskRecordMapper.selectById(RECORD_ID)).thenReturn(pendingRecord());
            when(mockRestTemplate.postForEntity(anyString(), any(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("{\"code\":200}", HttpStatus.OK));

            taskService.reviewTaskRecord(RECORD_ID, true, null);

            // ① 本地记录置通过、写发放时间、记录奖励金额快照
            ArgumentCaptor<UserTaskRecord> recCap = ArgumentCaptor.forClass(UserTaskRecord.class);
            verify(userTaskRecordMapper).updateById(recCap.capture());
            assertEquals(2, recCap.getValue().getStatus());
            assertNotNull(recCap.getValue().getRewardGrantedAt());
            assertEquals(0, recCap.getValue().getRewardAmount().compareTo(new BigDecimal("100.00")));

            // ② 任务维度统计累加
            ArgumentCaptor<Task> taskCap = ArgumentCaptor.forClass(Task.class);
            verify(taskMapper).updateById(taskCap.capture());
            assertEquals(Integer.valueOf(1), taskCap.getValue().getUsedQuota());
            assertEquals(0, taskCap.getValue().getUsedPoints().compareTo(new BigDecimal("100.00")));

            // ③ 确实调了 user-service 新接口（不再调 pay-service）
            ArgumentCaptor<String> urlCap = ArgumentCaptor.forClass(String.class);
            @SuppressWarnings("rawtypes")
            ArgumentCaptor<HttpEntity> entityCap = ArgumentCaptor.forClass(HttpEntity.class);
            verify(mockRestTemplate).postForEntity(urlCap.capture(), entityCap.capture(), eq(String.class));

            String url = urlCap.getValue();
            org.junit.jupiter.api.Assertions.assertTrue(
                    url.contains("/internal/earnings/credit"),
                    "应调用 user-service 内部入账接口，实际 URL=" + url);
            org.junit.jupiter.api.Assertions.assertNotNull(
                    entityCap.getValue().getHeaders().getFirst("X-Internal-Token"),
                    "请求应携带 X-Internal-Token");

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) entityCap.getValue().getBody();
            assertEquals(1, body.get("type"));                // type=1 任务收益
            assertEquals(RECORD_ID, body.get("taskRecordId"));
            assertEquals(USER_ID, body.get("userId"));
            assertEquals(0, new BigDecimal("100.00").compareTo((BigDecimal) body.get("amount")));
        }
    }

    // ======================== ③ 失败语义 ========================

    @Nested
    @DisplayName("user-service 调用失败 → 抛 GRANT_FAILED 且本地不落库")
    class CreditFailure {

        @Test
        @DisplayName("user-service 不可达（连接异常）→ 抛 BusinessException(GRANT_FAILED)，不更新本地")
        void remoteDown_throwsAndNoLocalCommit() {
            when(userTaskRecordMapper.selectById(2L)).thenReturn(pendingRecordWithId(2L));
            when(mockRestTemplate.postForEntity(anyString(), any(), eq(String.class)))
                    .thenThrow(new RuntimeException("connection refused"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> taskService.reviewTaskRecord(2L, true, null));

            assertEquals(ErrorCode.GRANT_FAILED.getCode(), ex.getCode());
            verify(userTaskRecordMapper, never()).updateById(any());
            verify(taskMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("user-service 返回非 2xx → 抛 BusinessException(GRANT_FAILED)")
        void non2xx_throws() {
            when(userTaskRecordMapper.selectById(3L)).thenReturn(pendingRecordWithId(3L));
            when(mockRestTemplate.postForEntity(anyString(), any(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("{\"code\":500}", HttpStatus.INTERNAL_SERVER_ERROR));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> taskService.reviewTaskRecord(3L, true, null));

            assertEquals(ErrorCode.GRANT_FAILED.getCode(), ex.getCode());
        }

        private UserTaskRecord pendingRecordWithId(long id) {
            UserTaskRecord r = new UserTaskRecord();
            r.setId(id);
            r.setUserId(USER_ID);
            r.setTaskId(TASK_ID);
            r.setStatus(1);
            return r;
        }
    }
}
