package com.task.platform.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.task.platform.admin.entity.UserTaskRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 用户任务记录 Mapper（管理后台用）
 * 操作 t_user_task_record 表
 */
@Mapper
public interface UserTaskRecordMapper extends BaseMapper<UserTaskRecord> {

    /**
     * 根据 taskId 联表查询领取记录（含用户手机号、昵称、任务奖励兜底）
     */
    @Select("""
            SELECT
                r.id,
                r.user_id           AS userId,
                r.task_id           AS taskId,
                r.status,
                r.submit_count      AS submitCount,
                COALESCE(r.reward_amount, t.reward_amount) AS rewardAmount,
                r.screenshot_url    AS screenshotUrl,
                r.accepted_at       AS acceptedAt,
                r.submitted_at      AS submittedAt,
                r.checked_at         AS checkedAt,
                r.review_result     AS reviewResult,
                r.submit_lat        AS submitLat,
                r.submit_lng        AS submitLng,
                u.phone,
                u.nickname,
                t.reward_amount     AS taskRewardAmount
            FROM t_user_task_record r
            LEFT JOIN t_user u ON u.id = r.user_id
            LEFT JOIN t_task t ON t.id = r.task_id
            WHERE r.task_id = #{taskId}
            ORDER BY r.accepted_at DESC
            """)
    List<Map<String, Object>> selectByTaskIdWithUser(Long taskId);

    /**
     * 按状态（可选）+ 商户（可选）联表查询领取记录列表（含用户 + 任务信息）
     * status 为 null 表示查全部；merchantId 为 null 表示查全部商户
     */
    @Select("""
            SELECT
                r.id,
                r.user_id           AS userId,
                r.task_id           AS taskId,
                r.status,
                r.submit_count      AS submitCount,
                COALESCE(r.reward_amount, t.reward_amount) AS rewardAmount,
                r.screenshot_url    AS screenshotUrl,
                r.accepted_at       AS acceptedAt,
                r.submitted_at      AS submittedAt,
                r.checked_at         AS checkedAt,
                r.review_result     AS reviewResult,
                r.submit_lat        AS submitLat,
                r.submit_lng        AS submitLng,
                u.phone,
                u.nickname,
                t.title             AS taskTitle,
                t.platform          AS taskPlatform,
                t.task_type         AS taskType,
                t.target_url        AS taskTargetUrl,
                t.reward_amount     AS taskRewardAmount
            FROM t_user_task_record r
            LEFT JOIN t_user u ON u.id = r.user_id
            LEFT JOIN t_task t ON t.id = r.task_id
            WHERE (r.status = #{status} OR #{status} IS NULL)
              AND (t.merchant_id = #{merchantId} OR #{merchantId} IS NULL)
            ORDER BY r.submitted_at DESC
            """)
    List<Map<String, Object>> selectByStatusWithUserAndTask(@Param("status") Integer status, @Param("merchantId") Long merchantId);

    /**
     * 根据 recordId 联表查询单条记录详情（含用户信息 + 任务信息）
     */
    @Select("""
            SELECT
                r.id,
                r.user_id           AS userId,
                r.task_id           AS taskId,
                r.status,
                r.submit_count      AS submitCount,
                r.screenshot_url    AS screenshotUrl,
                r.ai_check_result   AS aiCheckResult,
                r.review_result     AS reviewResult,
                r.reward_amount     AS rewardAmount,
                r.reward_granted_at AS rewardGrantedAt,
                r.accepted_at       AS acceptedAt,
                r.submitted_at      AS submittedAt,
                r.checked_at        AS checkedAt,
                r.submit_lat        AS submitLat,
                r.submit_lng        AS submitLng,
                u.phone,
                u.nickname,
                t.title             AS taskTitle,
                t.merchant_id       AS merchantId,
                t.platform          AS taskPlatform,
                t.task_type         AS taskType,
                t.target_url        AS taskTargetUrl,
                t.reward_amount     AS taskRewardAmount
            FROM t_user_task_record r
            LEFT JOIN t_user u ON u.id = r.user_id
            LEFT JOIN t_task t ON t.id = r.task_id
            WHERE r.id = #{recordId}
            """)
    Map<String, Object> selectByRecordIdWithUserAndTask(Long recordId);

    /**
     * 审核通过：更新状态为通过，写入奖励金额和审核时间
     */
    @Update("""
            UPDATE t_user_task_record
            SET status = 2,
                reward_amount = #{rewardAmount},
                checked_at = NOW()
            WHERE id = #{recordId}
            """)
    void approve(@Param("recordId") Long recordId, @Param("rewardAmount") BigDecimal rewardAmount);

    /**
     * 发放成功后写入发放时间
     */
    @Update("UPDATE t_user_task_record SET reward_granted_at = NOW() WHERE id = #{recordId}")
    void markGranted(@Param("recordId") Long recordId);

    /**
     * 审核拒绝：状态回退为进行中，写入拒绝原因和审核时间
     */
    @Update("""
            UPDATE t_user_task_record
            SET status = 0,
                checked_at = NOW(),
                review_result = #{reason}
            WHERE id = #{recordId}
            """)
    void reject(@Param("recordId") Long recordId, @Param("reason") String reason);

    /**
     * 根据记录ID查询任务奖励金额（用于写入收益明细）
     */
    @Select("""
            SELECT COALESCE(r.reward_amount, t.reward_amount) AS rewardAmount
            FROM t_user_task_record r
            LEFT JOIN t_task t ON t.id = r.task_id
            WHERE r.id = #{recordId}
            """)
    BigDecimal selectRewardAmount(Long recordId);
}
