package com.task.platform.user.service;

import com.task.platform.common.exception.BusinessException;
import com.task.platform.common.response.ErrorCode;
import com.task.platform.user.entity.User;
import com.task.platform.user.entity.UserEarnings;
import com.task.platform.user.entity.WithdrawRecord;
import com.task.platform.user.mapper.SysConfigMapper;
import com.task.platform.user.mapper.UserEarningsMapper;
import com.task.platform.user.mapper.UserMapper;
import com.task.platform.user.mapper.WithdrawRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WithdrawService 单元测试（无门槛提现）。
 *
 * <p>纯 Mockito：mock 4 个 mapper，覆盖
 * ① min_withdraw_amount=0 时余额>0 即可提、不报下限；
 * ② 余额不足拒绝；③ 实名未通过(real_auth_status≠2)拒绝；
 * ④ 单笔上限(>5000)拒绝；⑤ 金额<=0 拒绝；
 * ⑥ 反向确认 min_withdraw_amount>0 时仍校验下限。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WithdrawService 无门槛提现 单元测试")
class WithdrawServiceTest {

    @Mock
    private WithdrawRecordMapper withdrawRecordMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private SysConfigMapper sysConfigMapper;
    @Mock
    private UserEarningsMapper userEarningsMapper;

    @InjectMocks
    private WithdrawService withdrawService;

    /** 已实名通过的用户 */
    private User authedUser() {
        User u = new User();
        u.setId(1L);
        u.setRealName("张三");
        u.setRealAuthStatus(2); // 已认证
        return u;
    }

    // ======================== 无门槛：余额>0 即可提 ========================

    @Nested
    @DisplayName("无门槛提现（min_withdraw_amount=0）")
    class NoThreshold {

        @BeforeEach
        void stubNoThreshold() {
            when(sysConfigMapper.findValueByKey("min_withdraw_amount")).thenReturn("0");
        }

        @Test
        @DisplayName("余额>0、实名通过、提5元 → 成功，写 earnings(type=5,负额)+ withdraw(status=0)")
        void balancePositive_ok() {
            User u = authedUser();
            when(userMapper.selectById(1L)).thenReturn(u);
            when(userEarningsMapper.selectLatestBalance(1L)).thenReturn(new BigDecimal("100.00"));

            withdrawService.applyWithdraw(1L, new BigDecimal("5.00"), "alipay", "acct");

            verify(userEarningsMapper).insert(argThat(e ->
                    e.getType() == 5 && e.getAmount().compareTo(new BigDecimal("-5.00")) == 0));
            verify(withdrawRecordMapper).insert(argThat(r -> r.getStatus() == 0));
        }
    }

    // ======================== 拒绝分支 ========================

    @Nested
    @DisplayName("提现拒绝分支")
    class Rejections {

        @Test
        @DisplayName("余额不足 → 拒绝，不写流水")
        void insufficientBalance_rejected() {
            User u = authedUser();
            when(userMapper.selectById(1L)).thenReturn(u);
            when(sysConfigMapper.findValueByKey("min_withdraw_amount")).thenReturn("0");
            when(userEarningsMapper.selectLatestBalance(1L)).thenReturn(new BigDecimal("3.00"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> withdrawService.applyWithdraw(1L, new BigDecimal("5.00"), "alipay", "acct"));

            assertEquals(400, ex.getCode());
            verify(userEarningsMapper, never()).insert(any());
            verify(withdrawRecordMapper, never()).insert(any());
        }

        @Test
        @DisplayName("实名未通过(real_auth_status=1) → REAL_NAME_AUTH_REQUIRED(3002)")
        void realAuthNotPassed_rejected() {
            User u = new User();
            u.setId(1L);
            u.setRealAuthStatus(1); // 审核中
            when(userMapper.selectById(1L)).thenReturn(u);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> withdrawService.applyWithdraw(1L, new BigDecimal("5.00"), "alipay", "acct"));

            assertEquals(ErrorCode.REAL_NAME_AUTH_REQUIRED.getCode(), ex.getCode());
            verify(withdrawRecordMapper, never()).insert(any());
        }

        @Test
        @DisplayName("单笔超上限(>5000) → WITHDRAW_AMOUNT_TOO_LARGE(5002)")
        void exceedMax_rejected() {
            User u = authedUser();
            when(userMapper.selectById(1L)).thenReturn(u);
            when(sysConfigMapper.findValueByKey("min_withdraw_amount")).thenReturn("0");
            when(userEarningsMapper.selectLatestBalance(1L)).thenReturn(new BigDecimal("99999"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> withdrawService.applyWithdraw(1L, new BigDecimal("5000.01"), "alipay", "acct"));

            assertEquals(ErrorCode.WITHDRAW_AMOUNT_TOO_LARGE.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("金额<=0 → PARAM_ERROR(400)")
        void nonPositiveAmount_rejected() {
            User u = authedUser();
            when(userMapper.selectById(1L)).thenReturn(u);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> withdrawService.applyWithdraw(1L, new BigDecimal("0"), "alipay", "acct"));

            assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        }
    }

    // ======================== 反向确认下限开关 ========================

    @Nested
    @DisplayName("下限开关正确性（反向）")
    class ThresholdSwitch {

        @Test
        @DisplayName("min_withdraw_amount=10 时，提5元 → 报下限错误(5001)")
        void positiveThreshold_belowMin_rejected() {
            User u = authedUser();
            when(userMapper.selectById(1L)).thenReturn(u);
            when(sysConfigMapper.findValueByKey("min_withdraw_amount")).thenReturn("10");
            when(userEarningsMapper.selectLatestBalance(1L)).thenReturn(new BigDecimal("100"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> withdrawService.applyWithdraw(1L, new BigDecimal("5"), "alipay", "acct"));

            assertEquals(ErrorCode.WITHDRAW_AMOUNT_TOO_SMALL.getCode(), ex.getCode());
        }
    }
}
