package com.task.platform.user.service;

import com.task.platform.user.entity.UserEarnings;
import com.task.platform.user.mapper.UserEarningsMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EarningsService#credit 单元测试（任务奖励入账，幂等）。
 *
 * <p>纯 Mockito：mock UserEarningsMapper，覆盖设计文档 §1.4 的核心行为：
 * ① 首次入账写一条流水、余额 = prev + amount、type 默认 1；
 * ② 同 taskRecordId 重复调用命中幂等（selectByBizId 命中）不重复入账；
 * ③ 并发唯一索引冲突（DuplicateKeyException）兜底，重新查重返回 idempotent=true；
 * ④ selectLatestBalance 为 NULL 时按 0 累加。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EarningsService.credit 幂等入账 单元测试")
class EarningsServiceCreditTest {

    @Mock
    private UserEarningsMapper userEarningsMapper;

    @InjectMocks
    private EarningsService earningsService;

    private static final long USER_ID = 1L;
    private static final long TASK_RECORD_ID = 456L;
    private static final long TASK_ID = 789L;

    // ======================== ① 首次入账 ========================

    @Nested
    @DisplayName("首次入账")
    class FirstCredit {

        @Test
        @DisplayName("首次入账：写一条流水，余额=amount(无历史余额)，type 默认 1，idempotent=false")
        void firstCredit_writesOneRow() {
            when(userEarningsMapper.selectByBizId("456")).thenReturn(null);
            when(userEarningsMapper.selectLatestBalance(USER_ID)).thenReturn(null); // NULL -> 0

            EarningsService.CreditResult result =
                    earningsService.credit(USER_ID, TASK_RECORD_ID, TASK_ID, new BigDecimal("10.00"), null);

            assertFalse(result.isIdempotent());
            assertEquals("456", result.getBizId());
            assertEquals(0, result.getBalanceAfter().compareTo(new BigDecimal("10.00")));

            ArgumentCaptor<UserEarnings> captor = ArgumentCaptor.forClass(UserEarnings.class);
            verify(userEarningsMapper).insert(captor.capture());
            UserEarnings e = captor.getValue();
            assertEquals(USER_ID, e.getUserId());
            assertEquals(TASK_RECORD_ID, e.getRelatedId());
            assertEquals(1, e.getType());                       // type 默认 1
            assertEquals(0, e.getAmount().compareTo(new BigDecimal("10.00")));
            assertEquals(0, e.getBalanceAfter().compareTo(new BigDecimal("10.00")));
            assertEquals(1, e.getStatus());                    // status = 1 已到账
            assertEquals("456", e.getBizId());                 // biz_id = String.valueOf(taskRecordId)
            assertEquals("任务审核通过奖励入账", e.getRemark());
        }

        @Test
        @DisplayName("有历史余额：余额 = prev + amount")
        void firstCredit_withPrevBalance() {
            when(userEarningsMapper.selectByBizId("457")).thenReturn(null);
            when(userEarningsMapper.selectLatestBalance(USER_ID)).thenReturn(new BigDecimal("100.00"));

            EarningsService.CreditResult result =
                    earningsService.credit(USER_ID, 457L, TASK_ID, new BigDecimal("10.00"), 1);

            assertEquals(0, result.getBalanceAfter().compareTo(new BigDecimal("110.00")));
        }

        @Test
        @DisplayName("显式传入 type=2 时，写入的流水 type 为 2（不被默认覆盖）")
        void explicitType_isKept() {
            when(userEarningsMapper.selectByBizId("459")).thenReturn(null);
            when(userEarningsMapper.selectLatestBalance(USER_ID)).thenReturn(null);

            earningsService.credit(USER_ID, 459L, TASK_ID, new BigDecimal("5.00"), 2);

            ArgumentCaptor<UserEarnings> captor = ArgumentCaptor.forClass(UserEarnings.class);
            verify(userEarningsMapper).insert(captor.capture());
            assertEquals(2, captor.getValue().getType());
        }
    }

    // ======================== ② 幂等命中（前置查重） ========================

    @Nested
    @DisplayName("幂等命中（前置 selectByBizId）")
    class IdempotentHit {

        @Test
        @DisplayName("同 taskRecordId 二次调用：直接返回已有记录，idempotent=true，不写库")
        void duplicateCall_noSecondInsert() {
            UserEarnings existing = new UserEarnings();
            existing.setBalanceAfter(new BigDecimal("110.00"));
            when(userEarningsMapper.selectByBizId("456")).thenReturn(existing);

            EarningsService.CreditResult result =
                    earningsService.credit(USER_ID, TASK_RECORD_ID, TASK_ID, new BigDecimal("10.00"), 1);

            assertTrue(result.isIdempotent());
            assertEquals("456", result.getBizId());
            assertEquals(0, result.getBalanceAfter().compareTo(new BigDecimal("110.00")));
            verify(userEarningsMapper, never()).insert(any());
        }
    }

    // ======================== ③ 并发唯一索引冲突兜底 ========================

    @Nested
    @DisplayName("并发兜底（DuplicateKeyException）")
    class ConcurrencyFallback {

        @Test
        @DisplayName("insert 抛 DuplicateKeyException：重新查重返回 idempotent=true，不重复入账")
        void duplicateKeyException_fallsBackToIdempotent() {
            UserEarnings existing = new UserEarnings();
            existing.setBalanceAfter(new BigDecimal("200.00"));
            // 首次查重返回 null（并发插入前），重新查重返回已存在记录
            when(userEarningsMapper.selectByBizId("458")).thenReturn(null).thenReturn(existing);
            doThrow(new DuplicateKeyException("Duplicate entry for key 'uk_biz_id'"))
                    .when(userEarningsMapper).insert(any());

            EarningsService.CreditResult result =
                    earningsService.credit(USER_ID, 458L, TASK_ID, new BigDecimal("50.00"), 1);

            assertTrue(result.isIdempotent());
            assertEquals(0, result.getBalanceAfter().compareTo(new BigDecimal("200.00")));
            verify(userEarningsMapper).insert(any()); // 确实尝试过一次插入
        }
    }
}
