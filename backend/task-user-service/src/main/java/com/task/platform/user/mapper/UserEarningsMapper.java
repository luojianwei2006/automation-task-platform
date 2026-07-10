package com.task.platform.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.task.platform.user.entity.UserEarnings;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * 用户收益明细 Mapper（C端用户服务用）
 */
@Mapper
public interface UserEarningsMapper extends BaseMapper<UserEarnings> {

    /**
     * 获取用户最新的余额（最新一条记录的 balance_after）
     * 若无记录则返回 null
     */
    @Select("SELECT balance_after FROM t_user_earnings WHERE user_id = #{userId} AND status = 1 ORDER BY id DESC LIMIT 1")
    BigDecimal selectLatestBalance(@Param("userId") Long userId);

    /**
     * 按业务幂等键查询收益流水（用于入账幂等查重）
     * bizId = String.valueOf(taskRecordId)
     */
    @Select("SELECT * FROM t_user_earnings WHERE biz_id = #{bizId} LIMIT 1")
    UserEarnings selectByBizId(@Param("bizId") String bizId);

    /**
     * 统计用户累计收益（status=1已到账的记录）
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM t_user_earnings WHERE user_id = #{userId} AND status = 1")
    BigDecimal sumTotalEarnings(@Param("userId") Long userId);

    /**
     * 统计今日收益（status=1，created_at在今天）
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM t_user_earnings WHERE user_id = #{userId} AND status = 1 AND DATE(created_at) = CURDATE()")
    BigDecimal sumTodayEarnings(@Param("userId") Long userId);
}
