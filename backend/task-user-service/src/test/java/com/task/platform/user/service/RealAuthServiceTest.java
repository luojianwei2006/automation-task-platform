package com.task.platform.user.service;

import com.task.platform.common.exception.BusinessException;
import com.task.platform.user.entity.User;
import com.task.platform.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RealAuthService 单元测试（C端实名：提交持久化 + 审核通过/驳回 + 备注流转）。
 *
 * <p>纯 Mockito：mock UserMapper，验证
 * ① submit 持久化（状态=1 审核中、姓名/证件照URL、身份证 AES 占位加密、holdIdCardUrl）；
 * ② review 通过 → 状态=2、备注/审核时间已持久化；
 * ③ review 驳回 → 状态=3、备注=原因、审核时间已持久化；
 * ④ review 驳回未填原因 → 抛异常。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RealAuthService 实名提交/审核 单元测试")
class RealAuthServiceTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private RealAuthService realAuthService;

    /** 提交请求（合法身份证） */
    private RealAuthService.RealAuthRequest validRequest() {
        RealAuthService.RealAuthRequest req = new RealAuthService.RealAuthRequest();
        req.setRealName("张三");
        req.setIdCard("11010119900307123X");
        req.setIdCardFrontUrl("f");
        req.setIdCardBackUrl("b");
        req.setHoldIdCardUrl("h");
        return req;
    }

    // ======================== 提交 ========================

    @Nested
    @DisplayName("submitRealAuth 提交持久化")
    class Submit {

        @Test
        @DisplayName("提交 → 状态=1、姓名/证件照URL 持久化、身份证 AES 占位、holdIdCardUrl 写入")
        void submit_persistsPending() {
            User u = new User();
            u.setId(1L);
            u.setRealAuthStatus(0);
            when(userMapper.selectById(1L)).thenReturn(u);

            realAuthService.submitRealAuth(1L, validRequest());

            ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
            verify(userMapper).updateById(cap.capture());
            User saved = cap.getValue();
            assertEquals(RealAuthService.STATUS_PENDING, saved.getRealAuthStatus());
            assertEquals("张三", saved.getRealName());
            assertEquals("f", saved.getIdCardFrontUrl());
            assertEquals("h", saved.getHoldIdCardUrl());
            // 身份证经过 AES 占位加密（[ENCRYPTED] 前缀）
            org.junit.jupiter.api.Assertions.assertNotNull(saved.getIdCard());
            org.junit.jupiter.api.Assertions.assertTrue(saved.getIdCard().startsWith("[ENCRYPTED]"));
        }
    }

    // ======================== 审核 ========================

    @Nested
    @DisplayName("reviewRealAuth 审核流转")
    class Review {

        @BeforeEach
        void pendingUser() {
            User u = new User();
            u.setId(1L);
            u.setRealAuthStatus(RealAuthService.STATUS_PENDING);
            when(userMapper.selectById(1L)).thenReturn(u);
        }

        @Test
        @DisplayName("审核通过 → 状态=2、审核时间已写、备注可为空")
        void pass_setsPassed() {
            realAuthService.reviewRealAuth(1L, true, null);

            ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
            verify(userMapper).updateById(cap.capture());
            User saved = cap.getValue();
            assertEquals(RealAuthService.STATUS_PASSED, saved.getRealAuthStatus());
            org.junit.jupiter.api.Assertions.assertNotNull(saved.getRealAuthReviewedAt());
        }

        @Test
        @DisplayName("审核驳回（带原因）→ 状态=3、备注=原因、审核时间已写")
        void reject_setsFailedWithReason() {
            realAuthService.reviewRealAuth(1L, false, "照片不清晰");

            ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
            verify(userMapper).updateById(cap.capture());
            User saved = cap.getValue();
            assertEquals(RealAuthService.STATUS_FAILED, saved.getRealAuthStatus());
            assertEquals("照片不清晰", saved.getRealAuthRemark());
            org.junit.jupiter.api.Assertions.assertNotNull(saved.getRealAuthReviewedAt());
        }

        @Test
        @DisplayName("审核驳回未填原因 → 抛 BusinessException")
        void rejectWithoutReason_throws() {
            assertThrows(BusinessException.class,
                    () -> realAuthService.reviewRealAuth(1L, false, null));
            verify(userMapper, never()).updateById(any());
        }
    }
}
