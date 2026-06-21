package com.task.platform.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.task.platform.admin.entity.PublishTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 发布任务 Mapper（视频发布功能）
 */
@Mapper
public interface PublishTaskMapper extends BaseMapper<PublishTask> {
}
