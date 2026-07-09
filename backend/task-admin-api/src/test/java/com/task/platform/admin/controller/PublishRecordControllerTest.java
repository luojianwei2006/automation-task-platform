package com.task.platform.admin.controller;

import com.task.platform.admin.entity.Merchant;
import com.task.platform.admin.entity.PublishProject;
import com.task.platform.admin.entity.PublishTask;
import com.task.platform.admin.entity.UserPublishRecord;
import com.task.platform.admin.mapper.AppUserMapper;
import com.task.platform.admin.mapper.MerchantMapper;
import com.task.platform.admin.mapper.PublishProjectMapper;
import com.task.platform.admin.mapper.PublishTaskMapper;
import com.task.platform.admin.mapper.UserEarningsMapper;
import com.task.platform.admin.mapper.UserPublishRecordMapper;
import com.task.platform.admin.service.MerchantService;
import com.task.platform.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PublishRecordController.approve 审核通过（扣费落点）集成测试。
 *
 * <p>采用 @WebMvcTest + @MockBean 风格（与 UploadControllerTest 一致），
 * 隔离 DB / 商户服务等下游依赖，验证扣费时机、回滚与配额累加行为。</p>
 *
 * <p>事务回滚以「扣费抛异常后更新类 Mapper 不被调用」为断言依据：
 * 扣费失败整体回滚、记录保持 SUBMITTED。</p>
 */
@WebMvcTest(PublishRecordController.class)
@DisplayName("PublishRecordController 审核通过（扣费落点）集成测试")
class PublishRecordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserPublishRecordMapper userPublishRecordMapper;
    @MockBean
    private PublishTaskMapper publishTaskMapper;
    @MockBean
    private AppUserMapper appUserMapper;
    @MockBean
    private PublishProjectMapper publishProjectMapper;
    @MockBean
    private UserEarningsMapper userEarningsMapper;
    @MockBean
    private MerchantMapper merchantMapper;
    @MockBean
    private MerchantService merchantService;

    private UserPublishRecord submittedRecord;
    private PublishTask task;
    private PublishProject project;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        submittedRecord = new UserPublishRecord();
        submittedRecord.setId(100L);
        submittedRecord.setUserId(200L);
        submittedRecord.setTaskId(300L);
        submittedRecord.setStatus("SUBMITTED");

        task = new PublishTask();
        task.setId(300L);
        task.setProjectId(400L);
        task.setRewardAmount(new BigDecimal("10.00"));
        task.setUsedQuota(0);
        task.setUsedPoints(BigDecimal.ZERO);

        project = new PublishProject();
        project.setId(400L);
        project.setName("测试项目");
        project.setMerchantId(500L);

        merchant = new Merchant();
        merchant.setId(500L);
        merchant.setServiceFeeRate(new BigDecimal("0.15"));
        merchant.setPointBalance(new BigDecimal("1000.00"));

        when(userPublishRecordMapper.selectById(100L)).thenReturn(submittedRecord);
        when(publishTaskMapper.selectById(300L)).thenReturn(task);
        when(publishProjectMapper.selectById(400L)).thenReturn(project);
        when(merchantMapper.selectById(500L)).thenReturn(merchant);
        when(userEarningsMapper.selectLatestBalance(200L)).thenReturn(BigDecimal.ZERO);
        // 默认：扣费成功（无异常）
        doNothing().when(merchantService).deductTaskCost(any(), any(), any(), any());
    }

    // ======================== 场景一：扣费成功（余额充足） ========================

    @Nested
    @DisplayName("场景一：扣费成功（余额充足）")
    class DeductSuccess {

        @Test
        @DisplayName("余额充足 → 扣费被调用、流水生成、used 递增、status=PASSED")
        void shouldApproveAndDeduct() throws Exception {
            mockMvc.perform(post("/publish/records/100/approve"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            // 扣费被调用：merchantId=500, reward=10.00, recordId=100, projectName=测试项目
            verify(merchantService).deductTaskCost(
                    eq(500L),
                    eq(new BigDecimal("10.00")),
                    eq(100L),
                    eq("测试项目"));

            // 用户收益流水写入
            verify(userEarningsMapper).insertEarning(anyMap());

            // 记录状态更新为 PASSED
            verify(userPublishRecordMapper).updateById(
                    argThat(r -> "PASSED".equals(r.getStatus())));

            // 任务已用配额 +1、已消耗预算累加
            verify(publishTaskMapper).updateById(argThat(t -> {
                boolean quotaOk = t.getUsedQuota() != null && t.getUsedQuota() == 1;
                boolean pointsOk = t.getUsedPoints() != null
                        && t.getUsedPoints().compareTo(new BigDecimal("11.50")) == 0; // 10 + 10*0.15
                return quotaOk && pointsOk;
            }));
        }
    }

    // ======================== 场景二：余额不足（扣费失败回滚） ========================

    @Nested
    @DisplayName("场景二：余额不足（扣费失败整体回滚）")
    class DeductFail {

        @BeforeEach
        void setupFail() {
            doThrow(new BusinessException(400, "商户余额不足（余额:..., 需扣除:...），请先充值"))
                    .when(merchantService).deductTaskCost(eq(500L), any(), any(), any());
        }

        @Test
        @DisplayName("deduct 抛异常 → 整体回滚、记录保持 SUBMITTED、不发放收益")
        void shouldRollbackWhenDeductFails() throws Exception {
            mockMvc.perform(post("/publish/records/100/approve"))
                    .andExpect(status().is5xxServerError());

            // 扣费被调用（且确实抛异常触发回滚）
            verify(merchantService).deductTaskCost(eq(500L), any(), any(), any());

            // 回滚：记录保持 SUBMITTED —— updateById 不应被调用
            verify(userPublishRecordMapper, never()).updateById(any());

            // 回滚：不应发放用户收益
            verify(userEarningsMapper, never()).insertEarning(anyMap());

            // 回滚：任务配额 / 预算不应被更新
            verify(publishTaskMapper, never()).updateById(any());

            // 原始记录对象状态未被修改
            org.junit.jupiter.api.Assertions.assertEquals("SUBMITTED", submittedRecord.getStatus());
        }
    }
}
