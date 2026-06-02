package com.task.platform.admin.controller;

import com.task.platform.admin.entity.SysConfig;
import com.task.platform.admin.service.SysConfigService;
import com.task.platform.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统设置接口
 * 提供配置项的查询与批量更新能力
 *
 * GET  /api/admin/settings  → 获取所有配置（含说明）
 * PUT  /api/admin/settings  → 批量更新配置
 *
 * @author TaskPlatform
 */
@Slf4j
@RestController
@RequestMapping("/admin/settings")
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigService sysConfigService;

    /**
     * 获取所有系统配置
     * GET /api/admin/settings
     *
     * 返回配置列表（含 id, configKey, configValue, description, createdAt, updatedAt）
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
    public ApiResponse<List<SysConfig>> getSettings() {
        List<SysConfig> configs = sysConfigService.getAllConfigEntities();
        return ApiResponse.success(configs);
    }

    /**
     * 批量更新系统配置
     * PUT /api/admin/settings
     *
     * 请求体示例：
     * {
     *   "upload_domain": "http://new-domain:8085",
     *   "app_name": "新应用名"
     * }
     */
    @PutMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> updateSettings(@RequestBody Map<String, String> body) {
        if (body == null || body.isEmpty()) {
            return ApiResponse.error(400, "配置数据不能为空");
        }

        sysConfigService.updateConfigs(body);
        log.info("[SysConfig] 批量更新配置: {} 项", body.size());

        return ApiResponse.success(null, "配置更新成功");
    }
}
