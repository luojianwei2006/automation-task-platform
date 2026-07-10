package com.task.platform.task.service;

import com.task.platform.common.exception.BusinessException;
import com.task.platform.task.entity.Task;
import com.task.platform.task.entity.UserTaskRecord;
import com.task.platform.task.mapper.TaskMapper;
import com.task.platform.task.mapper.UserTaskRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TaskService.submitTask 状态机 单元测试（接任务→提交→审核 闭环的提交环节）。
 *
 * <p>纯 Mockito：mock userTaskRecordMapper / taskMapper，覆盖
 * ① 首次提交(status=0) → 进入审核中(1)，submitCount=1；
 * ② 驳回(3)后重提一次 → 审核中(1)，submitCount=2；
 * ③ 第三次提交(submitCount>=2) → 拒绝；
 * ④ 非 0/3 状态提交 → 拒绝。</p>
 *
 * <p>注：submitTask 不触发内部 HTTP 发奖（仅 reviewTaskRecord 触发），
 * 因此无需 stub 任何 HTTP 依赖，@Value 字段为 null 不影响本测试。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskService.submitTask 状态机 单元测试")
class TaskServiceSubmitTest {

    @Mock
    private UserTaskRecordMapper userTaskRecordMapper;
    @Mock
    private TaskMapper taskMapper;

    @InjectMocks
    private TaskService taskService;

    private static final Long USER_ID = 1L;
    private static final Long TASK_ID = 10L;

    @BeforeEach
    void setUp() {
        // 驳回重提分支会读取 Task 以重置提交截止时间（null → 默认 24h）
        when(taskMapper.selectById(TASK_ID)).thenReturn(new Task());
    }

    private UserTaskRecord baseRecord() {
        UserTaskRecord r = new UserTaskRecord();
        r.setId(1L);
        r.setUserId(USER_ID);
        r.setTaskId(TASK_ID);
        return r;
    }

    // ======================== 首次提交 ========================

    @Nested
    @DisplayName("首次提交 / 状态流转")
    class FirstSubmit {

        @Test
        @DisplayName("status=0 首次提交 → 审核中(1)，submitCount=1")
        void firstSubmit_toReviewing() {
            UserTaskRecord r = baseRecord();
            r.setStatus(0);
            r.setSubmitCount(0);
            when(userTaskRecordMapper.selectOne(any())).thenReturn(r);

            UserTaskRecord res = taskService.submitTask(USER_ID, TASK_ID, List.of("u1"), 1.0, 2.0);

            assertEquals(1, res.getStatus());
            assertEquals(1, res.getSubmitCount());
            verify(userTaskRecordMapper).updateById(any(UserTaskRecord.class));
        }
    }

    // ======================== 驳回重提 ========================

    @Nested
    @DisplayName("驳回后重提")
    class ResubmitAfterReject {

        @Test
        @DisplayName("status=3 且 submitCount=1 → 重提成功，审核中(1)，submitCount=2")
        void resubmit_once_toReviewing() {
            UserTaskRecord r = baseRecord();
            r.setStatus(3);
            r.setSubmitCount(1);
            when(userTaskRecordMapper.selectOne(any())).thenReturn(r);

            UserTaskRecord res = taskService.submitTask(USER_ID, TASK_ID, List.of("u1"), 1.0, 2.0);

            assertEquals(1, res.getStatus());
            assertEquals(2, res.getSubmitCount());
            verify(userTaskRecordMapper).updateById(any(UserTaskRecord.class));
        }

        @Test
        @DisplayName("第三次提交(submitCount=2) → 拒绝，不写库")
        void thirdAttempt_rejected() {
            UserTaskRecord r = baseRecord();
            r.setStatus(3);
            r.setSubmitCount(2);
            when(userTaskRecordMapper.selectOne(any())).thenReturn(r);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> taskService.submitTask(USER_ID, TASK_ID, List.of("u1"), 1.0, 2.0));

            assertEquals(400, ex.getCode());
            verify(userTaskRecordMapper, never()).updateById(any());
        }
    }

    // ======================== 非法状态 ========================

    @Nested
    @DisplayName("非法状态提交")
    class InvalidStatus {

        @Test
        @DisplayName("status=2(已通过) 提交 → 拒绝")
        void passedStatus_rejected() {
            UserTaskRecord r = baseRecord();
            r.setStatus(2);
            r.setSubmitCount(0);
            when(userTaskRecordMapper.selectOne(any())).thenReturn(r);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> taskService.submitTask(USER_ID, TASK_ID, List.of("u1"), 1.0, 2.0));

            assertEquals(400, ex.getCode());
            verify(userTaskRecordMapper, never()).updateById(any());
        }
    }
}
