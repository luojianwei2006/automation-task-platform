package com.task.platform.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.task.platform.user.entity.InviteRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * 邀请关系Mapper
 */
@Mapper
public interface InviteRelationMapper extends BaseMapper<InviteRelation> {

    /**
     * 根据被邀请人ID查询邀请关系
     */
    @Select("SELECT * FROM t_invite_relation WHERE invitee_id = #{inviteeId} LIMIT 1")
    InviteRelation selectByInviteeId(Long inviteeId);

    /**
     * 统计某邀请人的累计返佣金额
     */
    @Select("SELECT COALESCE(SUM(commission_amount), 0) FROM t_invite_relation WHERE inviter_id = #{inviterId}")
    BigDecimal sumCommissionByInviterId(Long inviterId);

    /**
     * 统计某邀请人的邀请人数
     */
    @Select("SELECT COUNT(1) FROM t_invite_relation WHERE inviter_id = #{inviterId}")
    long countByInviterId(Long inviterId);
}
