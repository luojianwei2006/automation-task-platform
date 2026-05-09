package com.task.platform.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.task.platform.admin.entity.Task;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务 Mapper（管理后台用）
 */
@Mapper
public interface TaskMapper extends BaseMapper<Task> {
}
