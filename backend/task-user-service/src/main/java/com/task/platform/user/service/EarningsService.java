package com.task.platform.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.task.platform.user.entity.UserEarnings;
import com.task.platform.user.mapper.UserEarningsMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EarningsService {

    private final UserEarningsMapper userEarningsMapper;

    /**
     * 收益概览
     */
    public EarningsSummaryVO getSummary(Long userId) {
        BigDecimal totalEarnings = userEarningsMapper.sumTotalEarnings(userId);
        BigDecimal availableBalance = userEarningsMapper.selectLatestBalance(userId);
        BigDecimal todayEarnings = userEarningsMapper.sumTodayEarnings(userId);

        EarningsSummaryVO vo = new EarningsSummaryVO();
        vo.setTotalEarnings(totalEarnings != null ? totalEarnings.doubleValue() : 0.0);
        vo.setAvailableBalance(availableBalance != null ? availableBalance.doubleValue() : 0.0);
        vo.setTodayEarnings(todayEarnings != null ? todayEarnings.doubleValue() : 0.0);
        return vo;
    }

    /**
     * 收益明细记录（分页）
     */
    public Page<EarningsRecordVO> getRecords(Long userId, Integer type, int page, int size) {
        LambdaQueryWrapper<UserEarnings> wrapper = new LambdaQueryWrapper<UserEarnings>()
                .eq(UserEarnings::getUserId, userId)
                .eq(UserEarnings::getStatus, 1) // 只查已到账
                .orderByDesc(UserEarnings::getId);

        if (type != null) {
            wrapper.eq(UserEarnings::getType, type);
        }

        Page<UserEarnings> entityPage = userEarningsMapper.selectPage(new Page<>(page, size), wrapper);

        List<EarningsRecordVO> records = entityPage.getRecords().stream().map(e -> {
            EarningsRecordVO vo = new EarningsRecordVO();
            vo.setId(e.getId());
            vo.setType(e.getType());
            vo.setDescription(e.getRemark() != null ? e.getRemark() : getTypeLabel(e.getType()));
            vo.setAmount(e.getAmount() != null ? e.getAmount().doubleValue() : 0.0);
            vo.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : "");
            return vo;
        }).collect(Collectors.toList());

        Page<EarningsRecordVO> resultPage = new Page<>(page, size, entityPage.getTotal());
        resultPage.setRecords(records);
        return resultPage;
    }

    /**
     * 任务奖励入账（幂等，内部接口 {@code POST /internal/earnings/credit} 调用）。
     *
     * <p>幂等键为 {@code bizId = String.valueOf(taskRecordId)}：
     * <ol>
     *   <li>插入前先 {@code selectByBizId} 查重，命中则直接返回已有记录（idempotent=true），不重复入账；</li>
     *   <li>{@code balance_after = selectLatestBalance(userId) + amount}（NULL→0）；</li>
     *   <li>插入流水（status=1）；并发唯一索引冲突（DuplicateKeyException）视为已入账，重新查重返回。</li>
     * </ol>
     * </p>
     *
     * @param userId       用户ID（必填）
     * @param taskRecordId 用户任务记录ID（必填，幂等键）
     * @param taskId       任务ID（可空）
     * @param amount       奖励金额（必填，正数）
     * @param type         收益类型（选填，默认 1=任务收益）
     * @return 入账结果（含 bizId、balanceAfter、idempotent）
     */
    public CreditResult credit(Long userId, Long taskRecordId, Long taskId, BigDecimal amount, Integer type) {
        String bizId = String.valueOf(taskRecordId);

        // 1. 幂等前置查重
        UserEarnings existing = userEarningsMapper.selectByBizId(bizId);
        if (existing != null) {
            return buildIdempotentResult(bizId, existing.getBalanceAfter());
        }

        // 2. 计算入账后余额（NULL→0）
        BigDecimal prev = userEarningsMapper.selectLatestBalance(userId);
        if (prev == null) {
            prev = BigDecimal.ZERO;
        }
        BigDecimal balanceAfter = prev.add(amount);

        // 3. 插入一条流水（status=1 已到账）
        UserEarnings earnings = new UserEarnings();
        earnings.setUserId(userId);
        earnings.setRelatedId(taskRecordId);
        earnings.setType(type != null ? type : 1);
        earnings.setAmount(amount);
        earnings.setBalanceAfter(balanceAfter);
        earnings.setStatus(1);
        earnings.setRemark("任务审核通过奖励入账");
        earnings.setBizId(bizId);
        earnings.setCreatedAt(LocalDateTime.now());

        try {
            userEarningsMapper.insert(earnings);
        } catch (DuplicateKeyException e) {
            // 并发竞态：唯一索引冲突，视为已入账，重新查重返回
            UserEarnings hit = userEarningsMapper.selectByBizId(bizId);
            return buildIdempotentResult(bizId, hit != null ? hit.getBalanceAfter() : balanceAfter);
        }

        CreditResult result = new CreditResult();
        result.setBizId(bizId);
        result.setBalanceAfter(balanceAfter);
        result.setIdempotent(false);
        return result;
    }

    /** 构造幂等命中结果 */
    private CreditResult buildIdempotentResult(String bizId, BigDecimal balanceAfter) {
        CreditResult result = new CreditResult();
        result.setBizId(bizId);
        result.setBalanceAfter(balanceAfter);
        result.setIdempotent(true);
        return result;
    }

    private String getTypeLabel(Integer type) {
        if (type == null) return "未知";
        return switch (type) {
            case 1 -> "任务收益";
            case 2 -> "广告奖励";
            case 3 -> "邀请返佣";
            case 4 -> "新手任务奖励";
            case 5 -> "提现";
            default -> "其他";
        };
    }

    // ─── VO ───

    /** 入账结果（内部接口返回） */
    @Data
    public static class CreditResult {
        /** 幂等键（taskRecordId 的字符串形式） */
        private String bizId;
        /** 入账后余额 */
        private BigDecimal balanceAfter;
        /** 是否幂等命中（同一 taskRecordId 已入账） */
        private boolean idempotent;
    }

    @Data
    public static class EarningsSummaryVO {
        private Double totalEarnings;
        private Double availableBalance;
        private Double todayEarnings;
    }

    @Data
    public static class EarningsRecordVO {
        private Long id;
        private Integer type;
        private String description;
        private Double amount;
        private String createdAt; // ISO 日期字符串
    }
}
