package com.task.platform.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.task.platform.admin.entity.MerchantTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 商户流水 Mapper
 */
@Mapper
public interface MerchantTransactionMapper extends BaseMapper<MerchantTransaction> {

    /**
     * 分页查询全部流水（联表查商户名）
     */
    @Select("<script>"
          + "SELECT t.id, t.merchant_id AS merchantId, t.type, t.amount, "
          + "       t.balance_before AS balanceBefore, t.balance_after AS balanceAfter, "
          + "       t.related_id AS relatedId, t.remark, t.created_at AS createdAt, "
          + "       m.name AS merchantName "
          + "FROM t_merchant_transaction t "
          + "LEFT JOIN t_merchant m ON t.merchant_id = m.id "
          + "WHERE 1=1 "
          + "<if test='merchantId != null'> AND t.merchant_id = #{merchantId} </if>"
          + "<if test='type != null'> AND t.type = #{type} </if>"
          + "<if test='startDate != null'> AND t.created_at &gt;= #{startDate} </if>"
          + "<if test='endDate != null'> AND t.created_at &lt;= #{endDate} </if>"
          + "ORDER BY t.created_at DESC "
          + "LIMIT #{offset}, #{limit}"
          + "</script>")
    List<Map<String, Object>> selectTransactionsWithMerchant(
            @Param("merchantId") Long merchantId,
            @Param("type") Integer type,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 查询流水总数（与上述查询条件一致）
     */
    @Select("<script>"
          + "SELECT COUNT(*) FROM t_merchant_transaction t "
          + "LEFT JOIN t_merchant m ON t.merchant_id = m.id "
          + "WHERE 1=1 "
          + "<if test='merchantId != null'> AND t.merchant_id = #{merchantId} </if>"
          + "<if test='type != null'> AND t.type = #{type} </if>"
          + "<if test='startDate != null'> AND t.created_at &gt;= #{startDate} </if>"
          + "<if test='endDate != null'> AND t.created_at &lt;= #{endDate} </if>"
          + "</script>")
    long countTransactions(
            @Param("merchantId") Long merchantId,
            @Param("type") Integer type,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);
}
