package com.task.platform.pay.security;

import com.task.platform.common.constant.InternalApiConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InternalApiFilter 单元测试（/pay/** 内部鉴权）。
 *
 * <p>纯 Mockito：mock HttpServletRequest/Response/FilterChain，
 * 用 ReflectionTestUtils 注入 expectedToken（等价于配置 internal.api-token）。
 * 验证：正确 token 放行、错误/缺失 token 返回 401、非 /pay 路径跳过过滤。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InternalApiFilter 内部接口鉴权 单元测试")
class InternalApiFilterTest {

    private final InternalApiFilter filter = new InternalApiFilter();

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain chain;

    @BeforeEach
    void setUp() throws Exception {
        // 等价于 @Value("${internal.api-token:task-internal-2026}")
        ReflectionTestUtils.setField(filter, "expectedToken", InternalApiConstants.DEFAULT_TOKEN);
        PrintWriter writer = mock(PrintWriter.class);
        when(response.getWriter()).thenReturn(writer);
    }

    // ======================== shouldNotFilter ========================

    @Nested
    @DisplayName("shouldNotFilter 路径判定")
    class PathDecision {

        @Test
        @DisplayName("非 /pay 路径 → shouldNotFilter=true（跳过过滤）")
        void nonPayPath_skipped() {
            when(request.getServletPath()).thenReturn("/health");
            assertTrue(filter.shouldNotFilter(request));
        }

        @Test
        @DisplayName("/pay/grant 路径 → shouldNotFilter=false（需要鉴权）")
        void payPath_filtered() {
            when(request.getServletPath()).thenReturn("/pay/grant");
            assertFalse(filter.shouldNotFilter(request));
        }
    }

    // ======================== doFilterInternal ========================

    @Nested
    @DisplayName("doFilterInternal 鉴权判定")
    class AuthDecision {

        @Test
        @DisplayName("携带正确 X-Internal-Token → 放行（chain.doFilter）")
        void correctToken_passes() throws Exception {
            when(request.getServletPath()).thenReturn("/pay/grant");
            when(request.getHeader(InternalApiConstants.HEADER_NAME)).thenReturn(InternalApiConstants.DEFAULT_TOKEN);

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).setStatus(anyInt());
        }

        @Test
        @DisplayName("携带错误 token → 401 拒绝，不放行")
        void wrongToken_rejected() throws Exception {
            when(request.getServletPath()).thenReturn("/pay/grant");
            when(request.getHeader(InternalApiConstants.HEADER_NAME)).thenReturn("wrong-token");

            filter.doFilterInternal(request, response, chain);

            verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
            verify(response.getWriter()).write(contains("401"));
            verify(chain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("缺失 token → 401 拒绝，不放行")
        void missingToken_rejected() throws Exception {
            when(request.getServletPath()).thenReturn("/pay/grant");
            when(request.getHeader(InternalApiConstants.HEADER_NAME)).thenReturn(null);

            filter.doFilterInternal(request, response, chain);

            verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
            verify(response.getWriter()).write(contains("401"));
            verify(chain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("空 token → 401 拒绝")
        void emptyToken_rejected() throws Exception {
            when(request.getServletPath()).thenReturn("/pay/grant");
            when(request.getHeader(InternalApiConstants.HEADER_NAME)).thenReturn("");

            filter.doFilterInternal(request, response, chain);

            verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
            verify(chain, never()).doFilter(any(), any());
        }
    }
}
