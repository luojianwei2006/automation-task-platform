package com.task.platform.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.task.platform.admin.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 管理员用户Mapper
 */
@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {

    @Select("SELECT * FROM t_admin_user WHERE username = #{username} LIMIT 1")
    AdminUser selectByUsername(String username);

    @Select("SELECT * FROM t_admin_user WHERE id = #{id} LIMIT 1")
    AdminUser selectById(@Param("id") Long id);

    @Select("SELECT COUNT(1) FROM t_admin_user WHERE merchant_id = #{merchantId} AND role_type = 2")
    long countMerchantAdmins(Long merchantId);
}
