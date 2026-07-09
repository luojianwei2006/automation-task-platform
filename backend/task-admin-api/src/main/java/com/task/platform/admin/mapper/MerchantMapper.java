package com.task.platform.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.task.platform.admin.entity.Merchant;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 商户 Mapper
 */
@Mapper
public interface MerchantMapper extends BaseMapper<Merchant> {

    /**
     * 根据手机号查询商户
     *
     * @param phone 手机号
     * @return 商户信息，不存在返回 null
     */
    @Select("SELECT * FROM t_merchant WHERE phone = #{phone} AND status != 0")
    Merchant selectByPhone(@Param("phone") String phone);

    /** 物理删除指定手机号的已删除记录（释放 UK 约束，用于重新注册） */
    @Delete("DELETE FROM t_merchant WHERE phone = #{phone} AND status = 0")
    void deleteByPhone(@Param("phone") String phone);
}
