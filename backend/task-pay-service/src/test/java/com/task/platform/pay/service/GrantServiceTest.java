package com.task.platform.pay.service;

import com.task.platform.pay.entity.RewardGrant;
import com.task.platform.pay.entity.UserEarnings;
import com.task.platform.pay.mapper.RewardGrantMapper;
import com.task.platform.pay.mapper.UserEarningsMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GrantService 单元测试（发奖幂等 + 余额累加）。
 *
 * <p>纯 Mockito 单元隔离：mock grantMapper / earningsMapper，验证
 * ① 正常发奖写入 t_reward_grant + t_user_earnings 且 balance_after 正确；
 * ② 同一 taskRecordId 调两次只发一次；③ 不同记录之间余额正确累加。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GrantService 发奖幂等 + 余额累加 单元测试")
class GrantServiceTest {

    @Mock
    private RewardGrantMapper grantMapper;
    @Mock
    private UserEarningsMapper earningsMapper;

    @InjectMocks
    private GrantService grantService;

    // ======================== 正常发奖 ========================

    @Nested
    @DisplayName("正常发奖：写发放记录 + 收益流水，balance_after 正确")
    class NormalGrant {

        @Test
        @DisplayName("首次发奖：prev=null→0，after=amount，写 grant+earnings(type=1)")
        void shouldInsertGrantAndEarnings() {
            when(grantMapper.selectByTaskRecordId(1L)).thenReturn(null);
            when(earningsMapper.selectLatestBalance(1L)).thenReturn(null);

            RewardGrant result = grantService.grant(1L, 1L, 10L, new BigDecimal("10.00"));

            assertNotNull(result);
            assertEquals(1, result.getStatus());
            verify(grantMapper).insert(any(RewardGrant.class));

            ArgumentCaptor<UserEarnings> cap = ArgumentCaptor.forClass(UserEarnings.class);
            verify(earningsMapper).insert(cap.capture());
            UserEarnings earnings = cap.getValue();
            assertEquals(1, earnings.getType());
            assertEquals(0, new BigDecimal("10.00").compareTo(earnings.getBalanceAfter()));
            assertEquals(1, earnings.getStatus());
        }
    }

    // ======================== 幂等 ========================

    @Nested
    @DisplayName("幂等：同一 taskRecordId 只发一次")
    class Idempotent {

        @Test
        @DisplayName("同一 taskRecordId 调用两次 grant → grant/earnings 各只写一次")
        void sameTaskRecordId_onlyOnce() {
            // 第一次查：未发放(null)；第二次查：已发放(返回既有记录)
            when(grantMapper.selectByTaskRecordId(1L))
                    .thenReturn(null)
                    .thenReturn(new RewardGrant());
            when(earningsMapper.selectLatestBalance(1L)).thenReturn(null);

            grantService.grant(1L, 1L, 10L, new BigDecimal("10.00"));
            grantService.grant(1L, 1L, 10L, new BigDecimal("10.00"));

            verify(grantMapper, times(1)).insert(any());
            verify(earningsMapper, times(1)).insert(any());
        }
    }

    // ======================== 余额累加 ========================

    @Nested
    @DisplayName("余额跨记录累加")
    class Accumulation {

        @Test
        @DisplayName("两条不同 taskRecordId 发放：10 + 20 → balance_after 0→10→30")
        void accumulatesAcrossRecords() {
            when(grantMapper.selectByTaskRecordId(anyLong())).thenReturn(null);
            // 第一笔 prev=null→0；第二笔读取到最新余额 10
            when(earningsMapper.selectLatestBalance(1L))
                    .thenReturn(null)
                    .thenReturn(new BigDecimal("10.00"));

            grantService.grant(1L, 1L, 10L, new BigDecimal("10.00")); // after=10
            grantService.grant(1L, 2L, 10L, new BigDecimal("20.00")); // after=30

            ArgumentCaptor<UserEarnings> cap = ArgumentCaptor.forClass(UserEarnings.class);
            verify(earningsMapper, times(2)).insert(cap.capture());
            List<UserEarnings> all = cap.getAllValues();
            assertEquals(0, new BigDecimal("10.00").compareTo(all.get(0).getBalanceAfter()));
            assertEquals(0, new BigDecimal("30.00").compareTo(all.get(1).getBalanceAfter()));
        }
    }
}
