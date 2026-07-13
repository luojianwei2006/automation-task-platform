package com.task.platform.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.task.platform.common.entity.Agreement;
import org.apache.ibatis.annotations.Mapper;

/**
 * 协议文档 Mapper（共享）
 * 供 admin-api（写）与 user-service（读）共用，需在两模块的 {@code @MapperScan} 中均包含
 * {@code com.task.platform.common.mapper} 包。
 *
 * @author TaskPlatform
 */
@Mapper
public interface AgreementMapper extends BaseMapper<Agreement> {
}
