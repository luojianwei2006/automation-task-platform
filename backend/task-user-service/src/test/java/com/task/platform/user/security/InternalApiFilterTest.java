package com.task.platform.user.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * InternalApiFilter 单元测试（仅 /internal/** 鉴权）。
 *
 * <p>直接调用 Servlet Filter，不依赖 Spring 上下文：
 * ① 非 /internal 路径 shouldNotFilter=true（放行）；
 * ② /internal 路径 shouldNotFilter=false（拦截）；
 * ③ 携带正确 X-Internal-Token → 放行（chain 继续执行，非 401）；
 * ④ 缺失 / 错误 token → 返回 401 且 chain 不执行。</p>
 */
@DisplayName("InternalApiFilter 内部接口鉴权 单元测试")
class InternalApiFilterTest {

    private static final String EXPECTED_TOKEN = "task-internal-2026";
    private static final String HEADER = "X-Internal-Token";

    private InternalApiFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        filter = new InternalApiFilter();
        // 通过反射注入 @Value 字段（无 Spring 上下文时默认 null）
        Field f = InternalApiFilter.class.getDeclaredField("expectedToken");
        f.setAccessible(true);
        f.set(filter, EXPECTED_TOKEN);
    }

    // ======================== shouldNotFilter 作用域 ========================

    @Nested
    @DisplayName("shouldNotFilter 作用域")
    class Scope {

        @Test
        @DisplayName("非 /internal 路径（如 /user/me）应跳过过滤")
        void nonInternal_skipped() {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/user/me");
            assertTrue(filter.shouldNotFilter(req));
        }

        @Test
        @DisplayName("/internal/earnings/credit 不应跳过过滤")
        void internal_notSkipped() {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/internal/earnings/credit");
            assertFalse(filter.shouldNotFilter(req));
        }
    }

    // ======================== 鉴权行为 ========================

    @Nested
    @DisplayName("doFilterInternal 鉴权行为")
    class Auth {

        @Test
        @DisplayName("正确 token → 放行（chain 执行，状态非 401）")
        void validToken_passes() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/internal/earnings/credit");
            req.addHeader(HEADER, EXPECTED_TOKEN);
            MockHttpServletResponse resp = new MockHttpServletResponse();
            AtomicBoolean chained = new AtomicBoolean(false);

            filter.doFilterInternal(req, resp, (r, rs) -> chained.set(true));

            assertTrue(chained.get(), "携带正确 token 时应放行过滤器链");
            assertEquals(HttpStatus.OK.value(), resp.getStatus(), "放行时不应返回 401");
        }

        @Test
        @DisplayName("错误 token → 401，chain 不执行")
        void wrongToken_returns401() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/internal/earnings/credit");
            req.addHeader(HEADER, "wrong-token");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            AtomicBoolean chained = new AtomicBoolean(false);

            filter.doFilterInternal(req, resp, (r, rs) -> chained.set(true));

            assertFalse(chained.get());
            assertEquals(HttpStatus.UNAUTHORIZED.value(), resp.getStatus());
            assertTrue(resp.getContentAsString().contains("Unauthorized"));
        }

        @Test
        @DisplayName("缺失 token → 401，chain 不执行")
        void missingToken_returns401() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/internal/earnings/credit");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            AtomicBoolean chained = new AtomicBoolean(false);

            filter.doFilterInternal(req, resp, (r, rs) -> chained.set(true));

            assertFalse(chained.get());
            assertEquals(HttpStatus.UNAUTHORIZED.value(), resp.getStatus());
        }
    }
}
