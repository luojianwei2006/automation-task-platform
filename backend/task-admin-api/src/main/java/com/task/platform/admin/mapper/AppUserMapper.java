package com.task.platform.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.task.platform.admin.entity.AppUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 普通用户 Mapper（管理后台用）
 * 操作 t_user 表
 */
@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {
}
