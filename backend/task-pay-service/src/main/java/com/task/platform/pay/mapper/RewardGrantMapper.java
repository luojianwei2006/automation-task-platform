package com.task.platform.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.task.platform.pay.entity.RewardGrant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 奖励发放记录 Mapper（pay-service 用）
 */
@Mapper
public interface RewardGrantMapper extends BaseMapper<RewardGrant> {

    /**
     * 按幂等键 task_record_id 查询是否已发放
     */
    @Select("SELECT * FROM t_reward_grant WHERE task_record_id = #{taskRecordId} LIMIT 1")
    RewardGrant selectByTaskRecordId(@Param("taskRecordId") Long taskRecordId);
}
