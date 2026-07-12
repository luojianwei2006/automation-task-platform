package com.task.platform.admin.controller;

import com.task.platform.admin.mapper.UserTaskRecordMapper;
import com.task.platform.admin.service.EarningsCreditClient;
import com.task.platform.admin.service.MerchantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminTaskRecordController#approve 单元测试（审核通过发奖流程）。
 *
 * <p>用 MockMvc(standaloneSetup) + Mockito mock 掉 EarningsCreditClient / MerchantService /
 * UserTaskRecordMapper（不依赖真实 DB），验证设计要点：
 * ① 首次通过(status=1)才扣商户费(deductTaskCost)，且只扣一次；
 * ② 幂等守卫(status==2 && rewardGrantedAt!=null)直接返回，不重复扣费/入账/markGranted；
 * ③ 半处理重试(status==2 但 rewardGrantedAt==null)跳过扣费，仍入账+markGranted；
 * ④ 把原 rewardGrantService.grant() 换成 earningsCreditClient.credit(..., type=1)；
 * ⑤ markGranted 在入账成功后调用；记录缺失 / 奖励金额缺失 返回 404/500。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminTaskRecordController.approve 审核发奖 单元测试")
class AdminTaskRecordControllerApproveTest {

    @Mock
    private UserTaskRecordMapper userTaskRecordMapper;
    @Mock
    private MerchantService merchantService;
    @Mock
    private EarningsCreditClient earningsCreditClient;

    private MockMvc mockMvc;

    private static final long RECORD_ID = 1L;
    private static final long USER_ID = 10L;
    private static final long TASK_ID = 20L;
    private static final long MERCHANT_ID = 5L;
    private static final BigDecimal REWARD = new BigDecimal("50.00");

    @BeforeEach
    void setUp() {
        AdminTaskRecordController controller =
                new AdminTaskRecordController(userTaskRecordMapper, merchantService, earningsCreditClient);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 构造一条审核详情 Map（含 controller 读取的所有字段） */
    private Map<String, Object> detail(Integer status, Object rewardGrantedAt,
                                        Long userId, Long taskId, Long merchantId, String taskTitle) {
        Map<String, Object> m = new HashMap<>();
        m.put("status", status);
        m.put("rewardGrantedAt", rewardGrantedAt);
        m.put("userId", userId);
        m.put("taskId", taskId);
        m.put("merchantId", merchantId);
        m.put("taskTitle", taskTitle);
        return m;
    }

    // ======================== ① 首次通过：扣费 + 入账 + markGranted ========================

    @Nested
    @DisplayName("首次通过(status=1)")
    class FirstApprove {

        @BeforeEach
        void stub() {
            when(userTaskRecordMapper.selectByRecordIdWithUserAndTask(RECORD_ID))
                    .thenReturn(detail(1, null, USER_ID, TASK_ID, MERCHANT_ID, "task title"));
            when(userTaskRecordMapper.selectRewardAmount(RECORD_ID)).thenReturn(REWARD);
        }

        @Test
        @DisplayName("首次通过：扣商户费1次 + 调 credit(type=1) + markGranted，HTTP 200")
        void firstApprove_deductAndCreditAndMark() throws Exception {
            mockMvc.perform(post("/admin/task-records/{id}/approve", RECORD_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            // ① 商户扣费仅一次（首次通过）
            verify(merchantService).deductTaskCost(MERCHANT_ID, REWARD, TASK_ID, "task title");
            // ② 调新接口入账，type=1
            verify(earningsCreditClient).credit(USER_ID, RECORD_ID, TASK_ID, REWARD, 1);
            // ③ approve + markGranted 均调用
            verify(userTaskRecordMapper).approve(RECORD_ID, REWARD);
            verify(userTaskRecordMapper).markGranted(RECORD_ID);
        }
    }

    // ======================== ② 幂等守卫 ========================

    @Nested
    @DisplayName("幂等守卫（已发放）")
    class IdempotentGuard {

        @Test
        @DisplayName("status=2 且 rewardGrantedAt!=null → 直接返回，不扣费/不入账/不markGranted")
        void alreadyGranted_noSideEffects() throws Exception {
            when(userTaskRecordMapper.selectByRecordIdWithUserAndTask(RECORD_ID))
                    .thenReturn(detail(2, LocalDateTime.now(), USER_ID, TASK_ID, MERCHANT_ID, "task title"));

            mockMvc.perform(post("/admin/task-records/{id}/approve", RECORD_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.msg").value("已发放，无需重复操作"));

            verify(merchantService, never()).deductTaskCost(any(), any(), any(), any());
            verify(earningsCreditClient, never()).credit(any(), any(), any(), any(), any());
            verify(userTaskRecordMapper, never()).approve(any(), any());
            verify(userTaskRecordMapper, never()).markGranted(any());
        }
    }

    // ======================== ③ 半处理重试（status=2 但 rewardGrantedAt=null） ========================

    @Nested
    @DisplayName("半处理重试（status=2，rewardGrantedAt=null）")
    class HalfProcessedRetry {

        @BeforeEach
        void stub() {
            when(userTaskRecordMapper.selectByRecordIdWithUserAndTask(RECORD_ID))
                    .thenReturn(detail(2, null, USER_ID, TASK_ID, MERCHANT_ID, "task title"));
            when(userTaskRecordMapper.selectRewardAmount(RECORD_ID)).thenReturn(REWARD);
        }

        @Test
        @DisplayName("已置通过但未发奖的重试：跳过扣费，仍入账+markGranted（避免双扣）")
        void retry_skipsDeductButStillCredits() throws Exception {
            mockMvc.perform(post("/admin/task-records/{id}/approve", RECORD_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(merchantService, never()).deductTaskCost(any(), any(), any(), any());
            verify(earningsCreditClient).credit(USER_ID, RECORD_ID, TASK_ID, REWARD, 1);
            verify(userTaskRecordMapper).markGranted(RECORD_ID);
        }
    }

    // ======================== ⑤ 异常分支 ========================

    @Nested
    @DisplayName("异常分支")
    class ErrorBranches {

        @Test
        @DisplayName("记录不存在 → 404，不扣费/不入账")
        void notFound_returns404() throws Exception {
            when(userTaskRecordMapper.selectByRecordIdWithUserAndTask(RECORD_ID)).thenReturn(null);

            mockMvc.perform(post("/admin/task-records/{id}/approve", RECORD_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404));

            verify(merchantService, never()).deductTaskCost(any(), any(), any(), any());
            verify(earningsCreditClient, never()).credit(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("奖励金额缺失 → 500，不扣费/不入账")
        void missingRewardAmount_returns500() throws Exception {
            when(userTaskRecordMapper.selectByRecordIdWithUserAndTask(RECORD_ID))
                    .thenReturn(detail(1, null, USER_ID, TASK_ID, MERCHANT_ID, "task title"));
            when(userTaskRecordMapper.selectRewardAmount(RECORD_ID)).thenReturn(null);

            mockMvc.perform(post("/admin/task-records/{id}/approve", RECORD_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500));

            verify(merchantService, never()).deductTaskCost(any(), any(), any(), any());
            verify(earningsCreditClient, never()).credit(any(), any(), any(), any(), any());
        }
    }
}
