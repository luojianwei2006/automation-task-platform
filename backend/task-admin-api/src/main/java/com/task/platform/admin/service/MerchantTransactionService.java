package com.task.platform.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.task.platform.admin.entity.MerchantTransaction;
import com.task.platform.admin.mapper.MerchantTransactionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商户流水服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantTransactionService {

    private final MerchantTransactionMapper transactionMapper;

    // 类型常量
    public static final int TYPE_RECHARGE = 1;       // 充值
    public static final int TYPE_TASK_COST = 2;      // 任务扣费（任务审核通过）
    public static final int TYPE_REFUND   = 3;       // 退款
    public static final int TYPE_MANUAL_DEDUCT = 4;  // 手动扣费（后台操作）

    /**
     * 新增流水记录
     */
    @Transactional(rollbackFor = Exception.class)
    public void addTransaction(Long merchantId, int type, BigDecimal amount,
                               BigDecimal balanceBefore, BigDecimal balanceAfter,
                               Long relatedId, String remark) {
        MerchantTransaction tx = new MerchantTransaction();
        tx.setMerchantId(merchantId);
        tx.setType(type);
        tx.setAmount(amount);
        tx.setBalanceBefore(balanceBefore);
        tx.setBalanceAfter(balanceAfter);
        tx.setRelatedId(relatedId);
        tx.setRemark(remark);
        tx.setCreatedAt(LocalDateTime.now());
        transactionMapper.insert(tx);
        log.info("[MerchantTx] 商户={} 类型={} 金额={} 余额: {}→{} remark={}",
            merchantId, type, amount, balanceBefore, balanceAfter, remark);
    }

    /**
     * 分页查询商户流水
     */
    public Page<MerchantTransaction> listTransactions(Long merchantId, int page, int size) {
        LambdaQueryWrapper<MerchantTransaction> wrapper = new LambdaQueryWrapper<MerchantTransaction>()
                .eq(MerchantTransaction::getMerchantId, merchantId)
                .orderByDesc(MerchantTransaction::getCreatedAt);
        return transactionMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /**
     * 全局查询流水（含商户名、筛选条件）
     *
     * @return {@code Map} 含 records（含 merchantName）和 total
     */
    public Map<String, Object> listGlobal(Long merchantId, Integer type,
                                          String startDate, String endDate,
                                          int page, int size) {
        int offset = (page - 1) * size;
        List<Map<String, Object>> records = transactionMapper.selectTransactionsWithMerchant(
                merchantId, type, startDate, endDate, offset, size);
        long total = transactionMapper.countTransactions(merchantId, type, startDate, endDate);
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }
}
