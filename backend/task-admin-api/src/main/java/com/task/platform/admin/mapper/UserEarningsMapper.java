package com.task.platform.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.task.platform.admin.entity.UserEarnings;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 用户收益明细 Mapper（管理后台用）
 * 操作 t_user_earnings 表
 */
@Mapper
public interface UserEarningsMapper extends BaseMapper<UserEarnings> {

    /**
     * 获取用户最新的余额（最新一条记录的 balance_after）
     * 若无记录则返回 null
     */
    @Select("SELECT balance_after FROM t_user_earnings WHERE user_id = #{userId} ORDER BY id DESC LIMIT 1")
    BigDecimal selectLatestBalance(Long userId);

    /**
     * 插入一条收益记录（自动计算 balance_after）
     */
    @Insert("INSERT INTO t_user_earnings (user_id, related_id, type, amount, balance_after, status, remark, created_at) " +
            "VALUES (#{userId}, #{relatedId}, #{type}, #{amount}, #{balanceAfter}, 1, #{remark}, NOW())")
    void insertEarning(Map<String, Object> params);

    /**
     * 分页查询用户收益流水
     */
    @Select("SELECT id, user_id, related_id, type, amount, balance_after, status, remark, created_at " +
            "FROM t_user_earnings " +
            "WHERE user_id = #{userId} " +
            "ORDER BY id DESC " +
            "LIMIT #{size} OFFSET #{offset}")
    List<Map<String, Object>> selectByUserIdWithPage(
            @Param("userId") Long userId,
            @Param("offset") long offset,
            @Param("size") int size);

    /**
     * 批量查询多个用户的最新余额
     * 返回每条记录：{ user_id, balance_after }
     */
    @Select("<script>" +
            "SELECT e1.user_id, e1.balance_after " +
            "FROM t_user_earnings e1 " +
            "WHERE e1.id IN (" +
            "  SELECT MAX(id) FROM t_user_earnings " +
            "  WHERE user_id IN " +
            "  <foreach collection='userIds' item='id' open='(' separator=',' close=')'>" +
            "    #{id}" +
            "  </foreach>" +
            "  GROUP BY user_id" +
            ")" +
            "</script>")
    List<Map<String, Object>> selectLatestBalanceBatch(@Param("userIds") List<Long> userIds);

    /**
     * 统计用户收益流水总数
     */
    @Select("SELECT COUNT(*) FROM t_user_earnings WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") Long userId);
}
