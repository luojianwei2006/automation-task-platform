package com.task.platform.pay.service;

import com.task.platform.common.exception.BusinessException;
import com.task.platform.common.response.ErrorCode;
import com.task.platform.pay.entity.RewardGrant;
import com.task.platform.pay.entity.UserEarnings;
import com.task.platform.pay.mapper.RewardGrantMapper;
import com.task.platform.pay.mapper.UserEarningsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 奖励发放服务（唯一权威发奖入口）
 *
 * <p>一致性策略：共享同一 MySQL 库 + 本地事务 + 幂等（t_reward_grant.task_record_id 唯一约束）
 * + 定时补偿（admin-api RewardGrantCompensationJob）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrantService {

    private final RewardGrantMapper grantMapper;
    private final UserEarningsMapper earningsMapper;

    /**
     * 发放任务奖励（幂等）。
     *
     * @param userId        用户ID
     * @param taskRecordId  用户任务记录ID（幂等键）
     * @param taskId        任务ID（可空）
     * @param amount        奖励金额
     * @return 发放记录（已存在则直接返回，不重复发放）
     */
    @Transactional(rollbackFor = Exception.class)
    public RewardGrant grant(Long userId, Long taskRecordId, Long taskId, BigDecimal amount) {
        // 1. 幂等：同一 taskRecordId 只发一次
        RewardGrant existing = grantMapper.selectByTaskRecordId(taskRecordId);
        if (existing != null) {
            log.info("[GrantService] 奖励已发放，命中幂等 taskRecordId={}, grantNo={}",
                    taskRecordId, existing.getGrantNo());
            return existing;
        }

        // 2. 计算新余额
        BigDecimal prev = earningsMapper.selectLatestBalance(userId);
        if (prev == null) {
            prev = BigDecimal.ZERO;
        }
        BigDecimal after = prev.add(amount);

        // 3. 写发放记录（status=1 已发放）
        String grantNo = generateGrantNo();
        RewardGrant grant = new RewardGrant();
        grant.setGrantNo(grantNo);
        grant.setUserId(userId);
        grant.setTaskId(taskId);
        grant.setTaskRecordId(taskRecordId);
        grant.setAmount(amount);
        grant.setStatus(1);
        grant.setBizId(String.valueOf(taskRecordId));
        grant.setCreatedAt(LocalDateTime.now());
        grant.setGrantedAt(LocalDateTime.now());

        try {
            grantMapper.insert(grant);
        } catch (DuplicateKeyException e) {
            // 并发竞态：另一条请求已先写入，直接返回已有记录（幂等兜底）
            RewardGrant dup = grantMapper.selectByTaskRecordId(taskRecordId);
            if (dup != null) {
                log.info("[GrantService] 并发竞态命中幂等 taskRecordId={}, grantNo={}", taskRecordId, dup.getGrantNo());
                return dup;
            }
            throw e;
        }

        // 4. 写余额流水（type=1 任务奖励）
        UserEarnings earnings = new UserEarnings();
        earnings.setUserId(userId);
        earnings.setRelatedId(taskRecordId);
        earnings.setType(1);
        earnings.setAmount(amount);
        earnings.setBalanceAfter(after);
        earnings.setStatus(1);
        earnings.setRemark("任务审核通过，奖励发放");
        earnings.setBizId(grantNo);
        earnings.setCreatedAt(LocalDateTime.now());
        earningsMapper.insert(earnings);

        log.info("[GrantService] 奖励发放成功 userId={}, taskRecordId={}, amount={}, grantNo={}",
                userId, taskRecordId, amount, grantNo);
        return grant;
    }

    private String generateGrantNo() {
        return "RG" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
