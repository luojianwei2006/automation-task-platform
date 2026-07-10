package com.task.platform.admin.schedule;

import com.task.platform.admin.mapper.UserTaskRecordMapper;
import com.task.platform.admin.service.RewardGrantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 奖励发放补偿任务（双保险之一）
 *
 * <p>扫描已审核通过（status=2）但未写发放时间（reward_granted_at IS NULL）的任务记录，
 * 重新调用 pay-service 发放奖励（幂等），确保最终一致（应对 pay-service 临时不可达）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RewardGrantCompensationJob {

    private final UserTaskRecordMapper userTaskRecordMapper;
    private final RewardGrantService rewardGrantService;

    /** 每分钟执行一次 */
    @Scheduled(cron = "0 * * * * ?")
    public void compensate() {
        try {
            List<Map<String, Object>> pending = userTaskRecordMapper.selectPendingGrants();
            if (pending == null || pending.isEmpty()) {
                return;
            }
            for (Map<String, Object> row : pending) {
                Long recordId = ((Number) row.get("id")).longValue();
                Long userId = row.get("userId") != null ? ((Number) row.get("userId")).longValue() : null;
                Long taskId = row.get("taskId") != null ? ((Number) row.get("taskId")).longValue() : null;
                BigDecimal amount = row.get("rewardAmount") != null
                        ? new BigDecimal(row.get("rewardAmount").toString()) : null;
                if (userId == null || amount == null) {
                    continue;
                }
                try {
                    rewardGrantService.grant(userId, recordId, taskId, amount);
                    userTaskRecordMapper.markGranted(recordId);
                    log.info("[RewardGrantCompensationJob] 补偿发放成功 recordId={}", recordId);
                } catch (Exception e) {
                    log.error("[RewardGrantCompensationJob] 补偿发放失败 recordId={}", recordId, e);
                }
            }
        } catch (Exception e) {
            log.error("[RewardGrantCompensationJob] 补偿任务异常", e);
        }
    }
}
