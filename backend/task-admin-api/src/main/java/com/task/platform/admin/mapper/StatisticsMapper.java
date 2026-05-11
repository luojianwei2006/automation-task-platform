package com.task.platform.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * 数据看板统计 Mapper
 *
 * @author TaskPlatform
 */
@Mapper
public interface StatisticsMapper {

    /**
     * 获取注册用户总数
     */
    @Select("SELECT COUNT(*) FROM t_user")
    Long getTotalUsers();

    /**
     * 获取任务总数
     */
    @Select("SELECT COUNT(*) FROM t_task")
    Long getTotalTasks();

    /**
     * 获取今日收益总额（已到账的收益）
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM t_user_earnings WHERE DATE(created_at) = CURDATE() AND status = 1")
    BigDecimal getTodayEarnings();

    /**
     * 获取待处理提现数量
     */
    @Select("SELECT COUNT(*) FROM t_withdraw_record WHERE status = 0")
    Long getPendingWithdrawCount();
}
