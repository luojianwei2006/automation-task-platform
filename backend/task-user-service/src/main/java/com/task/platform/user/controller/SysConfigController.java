package com.task.platform.user.controller;

import com.task.platform.common.response.ApiResponse;
import com.task.platform.user.entity.SysConfig;
import com.task.platform.user.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * C 端系统配置接口
 *
 * 提供 App 启动后拉取版本配置的能力（公开接口，无需登录）：
 *   GET /user/config  → 返回 app_version / app_download_url / app_name
 *
 * Gateway 路由规则：
 *   外部请求 /api/user/config
 *     → Gateway StripPrefix=1 去掉 /api
 *     → 转发到 user-service 的 /user/config
 *
 * 说明：
 *   - 运营后台在 sys_config 表维护 app_version / app_download_url / app_name 三个 KV；
 *   - 安卓端拉取后与本机 BuildConfig.VERSION_NAME 比对，决定是否需要更新。
 *
 * @author TaskPlatform
 */
@Slf4j
@RestController
@RequestMapping("/user/config")
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigMapper sysConfigMapper;

    /**
     * 获取 App 版本配置（C 端更新比对用）
     * GET /api/user/config
     *
     * @return 统一响应体，data 含：
     *   app_version       线上最新版本号（如 1.0.0）
     *   app_download_url  最新 APK 下载地址
     *   app_name          应用名称（可选，缺失时回退默认值）
     */
    @GetMapping
    public ApiResponse<Map<String, String>> getAppConfig() {
        Map<String, String> config = new LinkedHashMap<>(8);
        config.put("app_version", getConfigValue("app_version", ""));
        config.put("app_download_url", getConfigValue("app_download_url", ""));
        config.put("app_name", getConfigValue("app_name", "任务平台"));
        config.put("require_phone_verify", getConfigValue("require_phone_verify", "true"));
        return ApiResponse.success(config);
    }

    /**
     * 安全读取配置值：异常或缺失时返回默认值，避免接口 500。
     */
    private String getConfigValue(String key, String defaultVal) {
        try {
            SysConfig cfg = sysConfigMapper.selectByConfigKey(key);
            if (cfg != null && cfg.getConfigValue() != null && !cfg.getConfigValue().isEmpty()) {
                return cfg.getConfigValue();
            }
        } catch (Exception e) {
            log.warn("[SysConfig] 读取配置失败 key={}, error={}", key, e.getMessage());
        }
        return defaultVal;
    }
}
