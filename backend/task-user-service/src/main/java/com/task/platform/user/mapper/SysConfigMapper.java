package com.task.platform.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysConfigMapper {

    @Select("SELECT config_value FROM sys_config WHERE config_key = #{key} LIMIT 1")
    String findValueByKey(@Param("key") String key);
}
