package com.task.platform.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.task.platform.admin.entity.PublishMaterial;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * 素材 Mapper（视频发布功能）
 */
@Mapper
public interface PublishMaterialMapper extends BaseMapper<PublishMaterial> {

    /** 直接 SET deleted 标记（绕过 MyBatis-Plus 字段策略） */
    @Update("UPDATE t_material SET deleted = #{deleted} WHERE id = #{id}")
    int updateDeleted(Long id, int deleted);
}
