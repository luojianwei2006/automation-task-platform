package com.task.platform.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.task.platform.common.constant.InternalApiConstants;
import com.task.platform.common.exception.BusinessException;
import com.task.platform.common.response.ErrorCode;
import com.task.platform.task.entity.Task;
import com.task.platform.task.entity.UserTaskRecord;
import com.task.platform.task.mapper.TaskMapper;
import com.task.platform.task.mapper.UserTaskRecordMapper;
import com.task.platform.task.vo.MyTaskVO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.task.platform.common.response.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务服务
 * 任务发布、上下架、列表查询、用户接任务
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaskService {

    private final TaskMapper taskMapper;
    private final UserTaskRecordMapper userTaskRecordMapper;

    @Value("${admin.api.base-url:http://localhost:8084}")
    private String adminApiBaseUrl;

    @Value("${internal.api-token:}")
    private String internalApiToken;

    @Value("${user.api.base-url:http://localhost:8081}")
    private String userApiBaseUrl;

    private static final RestTemplate REST_TEMPLATE = new RestTemplate();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // 状态常量
    public static final int STATUS_PENDING = 0;   // 待审核
    public static final int STATUS_ONLINE = 1;     // 已上架
    public static final int STATUS_PAUSED = 2;     // 已暂停
    public static final int STATUS_ENDED  = 3;     // 已结束
    public static final int STATUS_REJECTED = 4;   // 已拒绝

    // 平台常量
    public static final int PLATFORM_DOUYIN = 1;  // 抖音
    public static final int PLATFORM_XIAOHONGSHU = 2; // 小红书

    // 任务类型常量
    public static final int TASK_TYPE_LIKE = 1;   // 点赞
    public static final int TASK_TYPE_COMMENT = 2;  // 评论

    /**
     * 发布任务（商户）
     */
    @Transactional(rollbackFor = Exception.class)
    public Task publishTask(Long merchantId, PublishTaskRequest req) {
        Task task = new Task();
        task.setMerchantId(merchantId);
        task.setTitle(req.getTitle());
        task.setPlatform(req.getPlatform());
        task.setTaskType(req.getTaskType());
        task.setTargetUrl(req.getTargetUrl());
        task.setRequirements(req.getRequirements());
        
        // 修复：空字符串转成 null，避免 MySQL JSON 字段报错
        String requirementImages = req.getRequirementImages();
        if (requirementImages == null || requirementImages.trim().isEmpty()) {
            task.setRequirementImages(null);
        } else {
            task.setRequirementImages(requirementImages);
        }
        
        task.setRewardAmount(req.getRewardAmount());
        task.setTotalQuota(req.getTotalQuota());
        task.setUsedQuota(0);
        task.setDailyLimit(req.getDailyLimit() != null ? req.getDailyLimit() : 0);
        task.setStatus(STATUS_PENDING); // 待审核
        task.setBudgetPoints(req.getBudgetPoints());
        task.setUsedPoints(BigDecimal.ZERO);
        task.setDeadline(req.getDeadline());

        taskMapper.insert(task);
        return task;
    }

    /**
     * 任务列表（分页 + 筛选）
     * 商户只能看自己的任务，超管看全部
     */
    public Page<Task> listTasks(int page, int size, Long merchantId,
                                Integer status, Integer platform, Integer taskType) {
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<Task>()
                .orderByDesc(Task::getCreatedAt);

        // 商户只能看自己的
        if (merchantId != null) {
            wrapper.eq(Task::getMerchantId, merchantId);
        }
        if (status != null) {
            wrapper.eq(Task::getStatus, status);
        }
        if (platform != null) {
            wrapper.eq(Task::getPlatform, platform);
        }
        if (taskType != null) {
            wrapper.eq(Task::getTaskType, taskType);
        }

        return taskMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /**
     * 任务详情
     */
    public Task getTaskDetail(Long taskId, Long merchantId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        // 商户只能看自己的任务
        if (merchantId != null && !task.getMerchantId().equals(merchantId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看此任务");
        }
        return task;
    }

    /**
     * 上下架任务（商户操作自己的任务）
     */
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long taskId, Long merchantId, boolean online) {
        Task task = getTaskDetail(taskId, merchantId);
        int newStatus = online ? STATUS_ONLINE : STATUS_PAUSED;
        task.setStatus(newStatus);
        if (online && task.getPublishedAt() == null) {
            task.setPublishedAt(LocalDateTime.now());
        }
        taskMapper.updateById(task);
    }

    /**
     * 审核任务（超管操作）
     */
    @Transactional(rollbackFor = Exception.class)
    public void reviewTask(Long taskId, boolean pass, String rejectReason) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        if (pass) {
            task.setStatus(STATUS_ONLINE);
            task.setPublishedAt(LocalDateTime.now());
        } else {
            task.setStatus(STATUS_REJECTED);
            task.setRejectReason(rejectReason);
        }
        taskMapper.updateById(task);
    }

    /**
     * 强制下架（超管操作）
     */
    @Transactional(rollbackFor = Exception.class)
    public void forceOffline(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        task.setStatus(STATUS_PAUSED);
        taskMapper.updateById(task);
    }

    // ==================== 用户端方法 ====================

    /**
     * 用户端任务列表（任务大厅）
     * 只返回已上架的任务
     */
    public Page<Task> getUserTaskList(int page, int size, Integer platform, Integer taskType) {
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<Task>()
                .eq(Task::getStatus, STATUS_ONLINE)  // 只显示上架中的任务
                .orderByDesc(Task::getCreatedAt);

        if (platform != null) {
            wrapper.eq(Task::getPlatform, platform);
        }
        if (taskType != null) {
            wrapper.eq(Task::getTaskType, taskType);
        }

        // 只展示可领取的任务：无截止时间，或剩余时间 > 30 分钟
        LocalDateTime claimableDeadline = LocalDateTime.now().plusMinutes(30);
        wrapper.and(w -> w.isNull(Task::getDeadline).or().gt(Task::getDeadline, claimableDeadline));

        Page<Task> result = taskMapper.selectPage(new Page<>(page, size), wrapper);
        
        // 调试日志
        log.info("===== 任务大厅查询 =====");
        log.info("查询条件: status={}", STATUS_ONLINE);
        log.info("结果总数: {}", result.getTotal());
        log.info("当前页记录数: {}", result.getRecords().size());
        
        // 打印每条记录的 requirementImages 字段
        for (Task task : result.getRecords()) {
            log.info("任务ID={}, title={}, requirementImages={}", 
                task.getId(), task.getTitle(), task.getRequirementImages());
        }
        
        return result;
    }

    /**
     * 用户端任务详情
     */
    public Task getTaskDetailForUser(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        
        // 调试日志：打印 requirementImages 字段
        log.info("===== 任务详情查询 =====");
        log.info("任务ID: {}", taskId);
        log.info("任务标题: {}", task.getTitle());
        log.info("requirementImages 字段值: {}", task.getRequirementImages());
        
        return task;
    }

    /**
     * 接受任务
     */
    @Transactional(rollbackFor = Exception.class)
    public UserTaskRecord acceptTask(Long userId, Long taskId) {
        // 1. 检查任务是否存在且已上架
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        if (task.getStatus() != STATUS_ONLINE) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "任务未上架");
        }

        // 2. 先查是否已有记录（已有记录=允许重接，不占配额）
        LambdaQueryWrapper<UserTaskRecord> recordWrapper = new LambdaQueryWrapper<UserTaskRecord>()
                .eq(UserTaskRecord::getUserId, userId)
                .eq(UserTaskRecord::getTaskId, taskId);
        UserTaskRecord existingRecord = userTaskRecordMapper.selectOne(recordWrapper);

        // 3. 配额检查（已有记录的重接不占配额）
        if (existingRecord == null && task.getUsedQuota() >= task.getTotalQuota()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "任务配额已用完");
        }

        // 4. 已有记录 → 重置状态后返回
        if (existingRecord != null) {
            existingRecord.setStatus(0);
            existingRecord.setAcceptedAt(LocalDateTime.now());
            existingRecord.setScreenshotUrl(null);
            existingRecord.setSubmittedAt(null);
            existingRecord.setSubmitCount(0);
            // 重置提交截止时间（原来没重置导致重接后立即超时）
            if (task.getSubmitDeadlineHours() != null && task.getSubmitDeadlineHours() > 0) {
                existingRecord.setAcceptDeadline(LocalDateTime.now().plusHours(task.getSubmitDeadlineHours()));
            } else {
                existingRecord.setAcceptDeadline(LocalDateTime.now().plusHours(24));
            }
            userTaskRecordMapper.updateById(existingRecord);
            return existingRecord;
        }

        // 4. 检查任务截止时间（剩余时间不足30分钟不能接取）
        if (task.getDeadline() != null) {
            LocalDateTime claimableBefore = task.getDeadline().minusMinutes(30);
            if (LocalDateTime.now().isAfter(claimableBefore)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "任务剩余时间不足30分钟，无法接取");
            }
        }

        // 5. 创建任务记录
        UserTaskRecord record = new UserTaskRecord();
        record.setUserId(userId);
        record.setTaskId(taskId);
        record.setStatus(0); // 进行中
        record.setSubmitCount(0);
        record.setAcceptedAt(LocalDateTime.now());

        // 6. 设置提交截止时间（如果有配置 submitDeadlineHours）
        if (task.getSubmitDeadlineHours() != null && task.getSubmitDeadlineHours() > 0) {
            record.setAcceptDeadline(LocalDateTime.now().plusHours(task.getSubmitDeadlineHours()));
        } else {
            // 默认24小时
            record.setAcceptDeadline(LocalDateTime.now().plusHours(24));
        }

        userTaskRecordMapper.insert(record);

        // 7. 增加任务已使用配额
        task.setUsedQuota(task.getUsedQuota() + 1);
        taskMapper.updateById(task);

        return record;
    }

    /**
     * 我的任务记录
     * 查询用户接取过的任务，关联 t_user_task_record + t_task，返回 Task 对象（含任务完整信息）
     */
    public Page<MyTaskVO> getMyTaskRecords(Long userId, int page, int size) {
        // 1. 查该用户全部记录，按 acceptedAt 降序（便于去重时保留最新）
        LambdaQueryWrapper<UserTaskRecord> wrapper = new LambdaQueryWrapper<UserTaskRecord>()
                .eq(UserTaskRecord::getUserId, userId)
                .orderByDesc(UserTaskRecord::getAcceptedAt);
        List<UserTaskRecord> allRecords = userTaskRecordMapper.selectList(wrapper);

        // 2. 终态优先去重：已通过(2)/已拒绝(3) 视为任务最终结果，优先展示；无终态则取最新
        java.util.List<UserTaskRecord> terminalRecords = new java.util.ArrayList<>();
        for (UserTaskRecord r : allRecords) {
            if (r.getStatus() == 2 || r.getStatus() == 3) terminalRecords.add(r); // 已降序，先到即终态中最新
        }
        java.util.Map<Long, UserTaskRecord> latestByTask = new java.util.LinkedHashMap<>();
        for (UserTaskRecord r : terminalRecords) {
            latestByTask.putIfAbsent(r.getTaskId(), r); // 终态任务：保留终态中最新
        }
        for (UserTaskRecord r : allRecords) {
            if (r.getStatus() != 2 && r.getStatus() != 3) {
                latestByTask.putIfAbsent(r.getTaskId(), r); // 非终态任务：仅当尚无终态记录时保留最新
            }
        }
        java.util.List<UserTaskRecord> distinctRecords = new java.util.ArrayList<>(latestByTask.values());

        // 3. 内存分页（按去重后的任务数分页）
        long total = distinctRecords.size();
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, distinctRecords.size());
        java.util.List<MyTaskVO> vos = new java.util.ArrayList<>();
        for (int i = fromIndex; i < toIndex; i++) {
            UserTaskRecord record = distinctRecords.get(i);
            Task task = taskMapper.selectById(record.getTaskId());
            if (task != null) {
                MyTaskVO vo = new MyTaskVO();
                BeanUtils.copyProperties(task, vo);
                vo.setRecordId(record.getId());
                vo.setRecordStatus(record.getStatus());
                vo.setSubmitCount(record.getSubmitCount());
                vo.setReviewResult(record.getReviewResult());
                vo.setAcceptedAt(record.getAcceptedAt());
                vo.setSubmittedAt(record.getSubmittedAt());
                vos.add(vo);
            }
        }

        Page<MyTaskVO> voPage = new Page<>(page, size, total);
        voPage.setRecords(vos);
        return voPage;
    }

    /**
     * 提交任务截图
     */
    @Transactional(rollbackFor = Exception.class)
    public UserTaskRecord submitTask(Long userId, Long taskId, List<String> screenshotUrls, Double latitude, Double longitude) {
        // 1. 查找任务记录
        LambdaQueryWrapper<UserTaskRecord> wrapper = new LambdaQueryWrapper<UserTaskRecord>()
                .eq(UserTaskRecord::getUserId, userId)
                .eq(UserTaskRecord::getTaskId, taskId);
        UserTaskRecord record = userTaskRecordMapper.selectOne(wrapper);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "未接取此任务");
        }

        // 2. 允许"进行中(0)"或"驳回(3 重提一次)"提交
        if (record.getStatus() != 0 && record.getStatus() != 3) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "任务状态不正确，无法提交");
        }

        // 驳回后重提：重置提交截止时间，给予新的提交窗口
        if (record.getStatus() == 3) {
            Task task = taskMapper.selectById(taskId);
            int hours = (task != null && task.getSubmitDeadlineHours() != null && task.getSubmitDeadlineHours() > 0)
                    ? task.getSubmitDeadlineHours() : 24;
            record.setAcceptDeadline(LocalDateTime.now().plusHours(hours));
        }

        // 3. 检查是否超时
        if (record.getAcceptDeadline() != null && LocalDateTime.now().isAfter(record.getAcceptDeadline())) {
            record.setStatus(4); // 超时放弃
            userTaskRecordMapper.updateById(record);
            throw new BusinessException(ErrorCode.PARAM_ERROR, "任务已超时，无法提交");
        }

        // 4. 检查提交次数（含驳回重提，最多 2 次）
        if (record.getSubmitCount() >= 2) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "提交次数已达上限");
        }

        // 5. 更新截图URL（多个URL用逗号拼接）、提交次数、状态
        String screenshotUrl = screenshotUrls != null && !screenshotUrls.isEmpty()
                ? String.join(",", screenshotUrls)
                : "";
        record.setScreenshotUrl(screenshotUrl);
        record.setSubmitCount(record.getSubmitCount() + 1);
        record.setStatus(1); // 待审核
        record.setSubmittedAt(LocalDateTime.now());
        record.setSubmitLat(latitude);
        record.setSubmitLng(longitude);
        userTaskRecordMapper.updateById(record);

        // TODO: 预留AI审核接口调用（后续实现）
        // aiCheckService.checkScreenshot(record.getId(), screenshotUrl);

        return record;
    }

    /**
     * 放弃任务（用户主动放弃进行中的任务）
     */
    @Transactional(rollbackFor = Exception.class)
    public void abandonTask(Long userId, Long taskId) {
        LambdaQueryWrapper<UserTaskRecord> wrapper = new LambdaQueryWrapper<UserTaskRecord>()
                .eq(UserTaskRecord::getUserId, userId)
                .eq(UserTaskRecord::getTaskId, taskId);
        UserTaskRecord record = userTaskRecordMapper.selectOne(wrapper);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "未接取此任务");
        }
        if (record.getStatus() != 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "只能放弃进行中的任务");
        }
        record.setStatus(4); // 超时放弃/主动放弃
        userTaskRecordMapper.updateById(record);

        // 归还名额
        Task task = taskMapper.selectById(taskId);
        if (task != null && task.getUsedQuota() > 0) {
            task.setUsedQuota(task.getUsedQuota() - 1);
            taskMapper.updateById(task);
        }
    }

    /**
     * 审核任务记录（人工审核，预留AI审核接口）
     * @param recordId 任务记录ID
     * @param pass 是否通过
     * @param reviewResult 审核结果（通过时可为null，拒绝时填写原因）
     */
    @Transactional(rollbackFor = Exception.class)
    public UserTaskRecord reviewTaskRecord(Long recordId, boolean pass, String reviewResult) {
        // 1. 查找任务记录
        UserTaskRecord record = userTaskRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务记录不存在");
        }

        // 2. 检查状态是否为"待审核"（1）
        if (record.getStatus() != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "任务状态不正确，无法审核");
        }

        // 3. 更新审核结果
        if (pass) {
            record.setStatus(2); // 通过
            record.setReviewResult("审核通过");
            record.setManualCheckedAt(LocalDateTime.now());
            record.setCheckedAt(LocalDateTime.now());

            // === 审核通过 → 调 user-service 内部接口入账（唯一权威发奖入口，幂等） ===
            // 说明：本端点为兼容端点，不再直接扣商户（商户扣费由 admin-api.approve 统一负责），
            // 仅触发入账；若 user-service 临时不可达，本方法 @Transactional 回滚，记录保持待审核可重试。
            Task task = taskMapper.selectById(record.getTaskId());
            if (task != null && task.getRewardAmount() != null
                    && task.getRewardAmount().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal rewardAmount = task.getRewardAmount();
                // 记录本笔奖励金额快照，供发放使用
                record.setRewardAmount(rewardAmount);
                // 触发入账：调 user-service 内部接口入账任务奖励（幂等，唯一权威发奖入口）
                creditUserEarnings(record.getUserId(), record.getId(), task.getId(), rewardAmount);
                record.setRewardGrantedAt(LocalDateTime.now());
                // 任务维度统计：已用配额 +1，已用点数累加（奖励额）
                task.setUsedQuota((task.getUsedQuota() == null ? 0 : task.getUsedQuota()) + 1);
                task.setUsedPoints((task.getUsedPoints() == null ? BigDecimal.ZERO : task.getUsedPoints())
                        .add(rewardAmount));
                taskMapper.updateById(task);
            }
        } else {
            record.setStatus(3); // 拒绝
            record.setReviewResult(reviewResult);
            record.setManualCheckedAt(LocalDateTime.now());
            record.setCheckedAt(LocalDateTime.now());
        }

        userTaskRecordMapper.updateById(record);
        return record;
    }

    /**
     * 调用管理后台内部接口，扣除商户任务费用（奖励 + 服务费）。
     * 扣款失败（余额不足/接口异常）时抛 BusinessException，由调用方事务回滚，确保"不扣款不通过"。
     */
    private void deductMerchantBalance(Long merchantId, BigDecimal rewardAmount,
                                       Long taskId, String taskTitle) {
        try {
            String url = adminApiBaseUrl + "/admin/merchants/" + merchantId + "/task-cost";
            Map<String, Object> body = new HashMap<>();
            body.put("rewardAmount", rewardAmount);
            body.put("taskId", taskId);
            body.put("taskTitle", taskTitle);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Token", internalApiToken);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = REST_TEMPLATE.postForEntity(url, entity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                String msg = "扣除商户余额失败";
                try {
                    if (resp.getBody() != null) {
                        ApiResponse<?> ar = OBJECT_MAPPER.readValue(resp.getBody(), ApiResponse.class);
                        if (ar.getCode() != 200) msg = ar.getMsg();
                    }
                } catch (Exception ignore) { /* 用默认提示 */ }
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, msg);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[TaskService] 调用管理后台扣费接口失败 merchantId={}, taskId={}", merchantId, taskId, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "扣除商户余额失败，请稍后重试");
        }
    }

    /**
     * 调 user-service 内部接口入账任务奖励（内部直连 + X-Internal-Token）
     * 幂等由 user-service 侧 t_user_earnings.biz_id 唯一索引保证（同一 taskRecordId 仅入账一次）。
     */
    private void creditUserEarnings(Long userId, Long taskRecordId, Long taskId, BigDecimal amount) {
        try {
            String url = userApiBaseUrl + "/internal/earnings/credit";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(InternalApiConstants.HEADER_NAME, internalApiToken);
            Map<String, Object> body = new HashMap<>();
            body.put("userId", userId);
            body.put("taskRecordId", taskRecordId);
            body.put("taskId", taskId);
            body.put("amount", amount);
            body.put("type", 1);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = REST_TEMPLATE.postForEntity(url, entity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                String msg = "奖励入账失败";
                try {
                    if (resp.getBody() != null) {
                        ApiResponse<?> ar = OBJECT_MAPPER.readValue(resp.getBody(), ApiResponse.class);
                        if (ar.getCode() != 200) {
                            msg = ar.getMsg();
                        }
                    }
                } catch (Exception ignore) {
                    // 用默认提示
                }
                throw new BusinessException(ErrorCode.GRANT_FAILED, msg);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[TaskService] 调用 user-service 入账失败 userId={}, taskRecordId={}", userId, taskRecordId, e);
            throw new BusinessException(ErrorCode.GRANT_FAILED, "奖励入账调用失败");
        }
    }

    /**
     * 查询任务记录详情（用于审核进度时间线）
     */
    public UserTaskRecord getTaskRecordDetail(Long recordId) {
        UserTaskRecord record = userTaskRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务记录不存在");
        }
        return record;
    }

    /**
     * 获取用户对指定任务的记录
     * 用于任务详情页判断用户是否已接取该任务
     */
    public UserTaskRecord getTaskRecord(Long userId, Long taskId) {
        LambdaQueryWrapper<UserTaskRecord> wrapper = new LambdaQueryWrapper<UserTaskRecord>()
                .eq(UserTaskRecord::getUserId, userId)
                .eq(UserTaskRecord::getTaskId, taskId)
                .orderByDesc(UserTaskRecord::getAcceptedAt);
        List<UserTaskRecord> records = userTaskRecordMapper.selectList(wrapper);
        if (records.isEmpty()) return null;
        // 终态优先：已通过(2)/已拒绝(3) 视为最终结果，优先返回（已降序，取最早终态）
        for (UserTaskRecord r : records) {
            if (r.getStatus() == 2 || r.getStatus() == 3) return r;
        }
        return records.get(0); // 无终态则取最新
    }

    /**
     * 处理超时任务（定时任务调用）
     * 将 status=0（进行中）且 acceptDeadline < now 的记录更新为 status=4（超时放弃）
     */
    @Transactional(rollbackFor = Exception.class)
    public int processTimeoutTasks() {
        LambdaQueryWrapper<UserTaskRecord> wrapper = new LambdaQueryWrapper<UserTaskRecord>()
                .eq(UserTaskRecord::getStatus, 0) // 进行中
                .lt(UserTaskRecord::getAcceptDeadline, LocalDateTime.now()); // 超时

        UserTaskRecord updateRecord = new UserTaskRecord();
        updateRecord.setStatus(4); // 超时放弃

        return userTaskRecordMapper.update(updateRecord, wrapper);
    }

    // ==================== DTO ====================

    @Data
    public static class PublishTaskRequest {
        private String title;
        private Integer platform;       // 1抖音 2小红书
        private Integer taskType;        // 1点赞 2评论
        private String targetUrl;
        private String requirements;
        private String requirementImages; // JSON数组字符串
        private BigDecimal rewardAmount;
        private Integer totalQuota;
        private Integer dailyLimit;
        private BigDecimal budgetPoints;
        private LocalDateTime deadline;
    }

    @Data
    public static class TaskVO {
        private Long id;
        private Long merchantId;
        private String title;
        private Integer platform;
        private Integer taskType;
        private String targetUrl;
        private BigDecimal rewardAmount;
        private Integer totalQuota;
        private Integer usedQuota;
        private Integer dailyLimit;
        private Integer status;
        private BigDecimal budgetPoints;
        private BigDecimal usedPoints;
        private LocalDateTime deadline;
        private LocalDateTime publishedAt;
        private LocalDateTime createdAt;
    }

    @Data
    public static class SubmitTaskRequest {
        private List<String> screenshotUrls;
        private Double latitude;
        private Double longitude;
    }
}
