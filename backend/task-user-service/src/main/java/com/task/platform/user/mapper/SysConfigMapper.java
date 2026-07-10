package com.task.platform.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.task.platform.user.entity.SysConfig;

@Mapper
public interface SysConfigMapper {

    @Select("SELECT config_value FROM sys_config WHERE config_key = #{key} LIMIT 1")
    String findValueByKey(@Param("key") String key);

    /**
     * 按 config_key 查询整行配置（供 C 端 /user/config 使用）。
     * 显式使用 AS 别名，避免依赖 mapUnderscoreToCamelCase 配置即可正确映射到实体字段。
     */
    @Select("SELECT id, config_key AS configKey, config_value AS configValue, description " +
            "FROM sys_config WHERE config_key = #{key} LIMIT 1")
    SysConfig selectByConfigKey(@Param("key") String key);
}
