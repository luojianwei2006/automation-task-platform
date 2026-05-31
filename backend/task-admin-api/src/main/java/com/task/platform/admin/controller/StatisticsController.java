package com.task.platform.admin.controller;

import com.task.platform.admin.mapper.StatisticsMapper;
import com.task.platform.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 数据看板统计接口
 *
 * @author TaskPlatform
 */
@Slf4j
@RestController
@RequestMapping("/admin/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsMapper statisticsMapper;

    /**
     * 获取管理后台数据看板统计信息
     * GET /api/admin/statistics/dashboard
     *
     * 返回：
     * - totalUsers: 注册用户总数
     * - totalTasks: 任务总数
     * - todayEarnings: 今日收益总额
     * - pendingWithdraw: 待处理提现数量
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN', 'FINANCE')")
    public ApiResponse<?> getDashboardStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // 注册用户总数
        Long totalUsers = statisticsMapper.getTotalUsers();
        log.info("统计数据 - 注册用户总数: {}", totalUsers);
        stats.put("totalUsers", totalUsers != null ? totalUsers : 0);

        // 任务总数
        Long totalTasks = statisticsMapper.getTotalTasks();
        log.info("统计数据 - 任务总数: {}", totalTasks);
        stats.put("totalTasks", totalTasks != null ? totalTasks : 0);

        // 今日收益总额
        BigDecimal todayEarnings = statisticsMapper.getTodayEarnings();
        log.info("统计数据 - 今日收益: {}", todayEarnings);
        stats.put("todayEarnings", todayEarnings != null ? todayEarnings : BigDecimal.ZERO);

        // 待处理提现数量
        Long pendingWithdraw = statisticsMapper.getPendingWithdrawCount();
        log.info("统计数据 - 待处理提现: {}", pendingWithdraw);
        stats.put("pendingWithdraw", pendingWithdraw != null ? pendingWithdraw : 0);

        log.info("数据看板统计结果: {}", stats);
        return ApiResponse.success(stats);
    }
}
