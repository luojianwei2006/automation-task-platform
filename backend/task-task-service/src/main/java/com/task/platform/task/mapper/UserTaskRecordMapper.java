package com.task.platform.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.task.platform.task.entity.UserTaskRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户任务记录 Mapper
 */
@Mapper
public interface UserTaskRecordMapper extends BaseMapper<UserTaskRecord> {
}
