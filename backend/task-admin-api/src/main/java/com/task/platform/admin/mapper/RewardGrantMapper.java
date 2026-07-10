package com.task.platform.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.task.platform.admin.entity.RewardGrant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 奖励发放记录 Mapper（管理后台用）
 */
@Mapper
public interface RewardGrantMapper extends BaseMapper<RewardGrant> {

    /**
     * 奖励发放记录列表（关联用户昵称/姓名、任务标题）
     */
    @Select("<script>" +
            "SELECT g.id, g.grant_no AS grantNo, g.user_id AS userId, g.task_id AS taskId, " +
            "       g.task_record_id AS taskRecordId, g.amount, g.status, g.biz_id AS bizId, " +
            "       g.created_at AS createdAt, g.granted_at AS grantedAt, " +
            "       u.nickname, u.real_name AS realName, t.title AS taskTitle " +
            "FROM t_reward_grant g " +
            "LEFT JOIN t_user u ON u.id = g.user_id " +
            "LEFT JOIN t_task t ON t.id = g.task_id " +
            "WHERE (g.user_id = #{userId} OR #{userId} IS NULL) " +
            "  AND (g.status = #{status} OR #{status} IS NULL) " +
            "ORDER BY g.id DESC " +
            "LIMIT #{size} OFFSET #{offset}" +
            "</script>")
    List<Map<String, Object>> selectListWithUserAndTask(@Param("userId") Long userId,
                                                        @Param("status") Integer status,
                                                        @Param("offset") long offset,
                                                        @Param("size") int size);

    /**
     * 奖励发放记录总数
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM t_reward_grant " +
            "WHERE (user_id = #{userId} OR #{userId} IS NULL) " +
            "  AND (status = #{status} OR #{status} IS NULL)" +
            "</script>")
    long count(@Param("userId") Long userId, @Param("status") Integer status);
}
