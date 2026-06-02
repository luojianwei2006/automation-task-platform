package com.task.platform.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.task.platform.common.exception.BusinessException;
import com.task.platform.common.response.ErrorCode;
import com.task.platform.user.entity.User;
import com.task.platform.user.entity.UserEarnings;
import com.task.platform.user.entity.WithdrawRecord;
import com.task.platform.user.mapper.SysConfigMapper;
import com.task.platform.user.mapper.UserEarningsMapper;
import com.task.platform.user.mapper.UserMapper;
import com.task.platform.user.mapper.WithdrawRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawService {

    private final WithdrawRecordMapper withdrawRecordMapper;
    private final UserMapper userMapper;
    private final SysConfigMapper sysConfigMapper;
    private final UserEarningsMapper userEarningsMapper;

    private static final BigDecimal DEFAULT_MIN_AMOUNT = new BigDecimal("10");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("5000");

    private BigDecimal getMinAmount() {
        try {
            String val = sysConfigMapper.findValueByKey("min_withdraw_amount");
            if (val != null && !val.isBlank()) {
                return new BigDecimal(val);
            }
        } catch (Exception e) {
            log.warn("读取最低提现金额失败: {}", e.getMessage());
        }
        return DEFAULT_MIN_AMOUNT;
    }

    private BigDecimal getCurrentBalance(Long userId) {
        BigDecimal bal = userEarningsMapper.selectLatestBalance(userId);
        return bal != null ? bal : BigDecimal.ZERO;
    }

    @Transactional(rollbackFor = Exception.class)
    public void applyWithdraw(Long userId, BigDecimal amount, String method, String account) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);

        if (user.getRealAuthStatus() == null || user.getRealAuthStatus() != 2) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请先完成实名认证");
        }

        BigDecimal minAmount = getMinAmount();
        if (amount.compareTo(minAmount) < 0) {
            throw new BusinessException(400, "提现金额不能低于" + minAmount + "元");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessException(ErrorCode.WITHDRAW_AMOUNT_TOO_LARGE);
        }

        // 余额校验 + 扣款
        BigDecimal balance = getCurrentBalance(userId);
        if (balance.compareTo(amount) < 0) {
            throw new BusinessException(400, "可提现余额不足，当前余额" + balance + "元");
        }
        BigDecimal balanceAfter = balance.subtract(amount);

        UserEarnings earnings = new UserEarnings();
        earnings.setUserId(userId);
        earnings.setType(3); // 提现
        earnings.setAmount(amount.negate()); // 负数 = 支出
        earnings.setBalanceAfter(balanceAfter);
        earnings.setStatus(1);
        earnings.setRemark("提现-" + method);
        earnings.setCreatedAt(LocalDateTime.now());
        userEarningsMapper.insert(earnings);

        String withdrawNo = "WD" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        WithdrawRecord record = new WithdrawRecord();
        record.setWithdrawNo(withdrawNo);
        record.setUserId(userId);
        record.setAmount(amount);
        record.setMethod(method);
        record.setAccount(account);
        record.setRealName(user.getRealName());
        record.setStatus(0);
        record.setCreatedAt(LocalDateTime.now());
        withdrawRecordMapper.insert(record);

        log.info("提现申请: userId={}, amount={}, 扣款后余额={}, withdrawNo={}", userId, amount, balanceAfter, withdrawNo);
    }

    public List<WithdrawRecord> getRecords(Long userId) {
        return withdrawRecordMapper.selectList(
                new LambdaQueryWrapper<WithdrawRecord>()
                        .eq(WithdrawRecord::getUserId, userId)
                        .orderByDesc(WithdrawRecord::getCreatedAt)
        );
    }
}
