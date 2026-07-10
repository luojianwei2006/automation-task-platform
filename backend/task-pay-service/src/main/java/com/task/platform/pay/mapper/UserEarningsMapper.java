package com.task.platform.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.task.platform.pay.entity.UserEarnings;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户收益明细 Mapper（pay-service 镜像，只读最新余额 / 写发放流水）
 */
@Mapper
public interface UserEarningsMapper extends BaseMapper<UserEarnings> {

    /**
     * 获取用户最新余额（status=1 的最新一条 balance_after）
     * 无记录返回 null
     */
    @Select("SELECT balance_after FROM t_user_earnings WHERE user_id = #{userId} AND status = 1 ORDER BY id DESC LIMIT 1")
    java.math.BigDecimal selectLatestBalance(@Param("userId") Long userId);
}
