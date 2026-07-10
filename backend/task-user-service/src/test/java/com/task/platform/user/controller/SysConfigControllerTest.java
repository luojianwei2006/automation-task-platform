package com.task.platform.user.controller;

import com.task.platform.common.response.ApiResponse;
import com.task.platform.user.entity.SysConfig;
import com.task.platform.user.mapper.SysConfigMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * SysConfigController#getAppConfig (GET /user/config) 单元测试。
 *
 * <p>纯 Mockito：mock SysConfigMapper，重点验证需求点
 * 「config key 缺失 / mapper 异常时返回默认值，不抛 500」。
 * 同时验证返回体用 ApiResponse<T>（code=200、data 含三个 key），
 * 与安卓端 AppUpdateManager 的 response.code==200 && response.data!=null 契约一致。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SysConfigController /user/config 单元测试")
class SysConfigControllerTest {

    @Mock
    private SysConfigMapper sysConfigMapper;

    @InjectMocks
    private SysConfigController controller;

    private SysConfig cfg(String value) {
        SysConfig c = new SysConfig();
        c.setConfigKey("k");
        c.setConfigValue(value);
        return c;
    }

    // ======================== 配置存在 ========================

    @Nested
    @DisplayName("配置存在时")
    class Present {

        @Test
        @DisplayName("app_version 存在 → 返回该值，ApiResponse.code=200")
        void versionPresent() {
            when(sysConfigMapper.selectByConfigKey("app_version")).thenReturn(cfg("1.2.3"));
            ApiResponse<Map<String, String>> resp = controller.getAppConfig();
            assertEquals(200, resp.getCode());
            Map<String, String> data = resp.getData();
            assertNotNull(data);
            assertEquals("1.2.3", data.get("app_version"));
        }

        @Test
        @DisplayName("app_name 存在 → 返回库中值而非默认『任务平台』")
        void appNamePresent() {
            when(sysConfigMapper.selectByConfigKey("app_name")).thenReturn(cfg("自动化任务平台"));
            ApiResponse<Map<String, String>> resp = controller.getAppConfig();
            assertEquals("自动化任务平台", resp.getData().get("app_name"));
            // 未 stub 的 key 返回空串默认，接口仍正常（不 500）
            assertEquals("", resp.getData().get("app_download_url"));
        }
    }

    // ======================== 配置缺失 / 异常（兜底） ========================

    @Nested
    @DisplayName("配置缺失 / mapper 异常时（兜底，不 500）")
    class Absent {

        @Test
        @DisplayName("app_version 缺失(null) → 回退空串，接口正常返回 code=200")
        void versionMissing_null() {
            when(sysConfigMapper.selectByConfigKey("app_version")).thenReturn(null);
            ApiResponse<Map<String, String>> resp = controller.getAppConfig();
            assertEquals(200, resp.getCode());
            assertEquals("", resp.getData().get("app_version"));
        }

        @Test
        @DisplayName("app_name 缺失 → 回退默认『任务平台』")
        void appNameMissing_default() {
            when(sysConfigMapper.selectByConfigKey("app_name")).thenReturn(null);
            ApiResponse<Map<String, String>> resp = controller.getAppConfig();
            assertEquals("任务平台", resp.getData().get("app_name"));
        }

        @Test
        @DisplayName("mapper 抛异常 → catch 后返回默认，不向上抛出（避免 500）")
        void mapperThrows_caught() {
            when(sysConfigMapper.selectByConfigKey("app_version"))
                    .thenThrow(new RuntimeException("db down"));
            ApiResponse<Map<String, String>> resp = controller.getAppConfig();
            assertEquals(200, resp.getCode());
            assertEquals("", resp.getData().get("app_version"));
        }
    }

    // ======================== 返回体形状 ========================

    @Nested
    @DisplayName("返回体字段完整性")
    class Shape {

        @Test
        @DisplayName("data 含 app_version / app_download_url / app_name 三个 key")
        void keysComplete() {
            ApiResponse<Map<String, String>> resp = controller.getAppConfig();
            Map<String, String> data = resp.getData();
            assertTrue(data.containsKey("app_version"));
            assertTrue(data.containsKey("app_download_url"));
            assertTrue(data.containsKey("app_name"));
        }
    }
}
