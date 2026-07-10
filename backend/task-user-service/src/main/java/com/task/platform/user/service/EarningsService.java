package com.task.platform.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.task.platform.user.entity.UserEarnings;
import com.task.platform.user.mapper.UserEarningsMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
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
