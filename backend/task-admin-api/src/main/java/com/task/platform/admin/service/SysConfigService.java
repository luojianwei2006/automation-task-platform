package com.task.platform.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.task.platform.admin.entity.SysConfig;
import com.task.platform.admin.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统配置服务
 * 提供配置的查询与批量 upsert 能力
 *
 * @author TaskPlatform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysConfigService {

    private final SysConfigMapper sysConfigMapper;

    /**
     * 获取所有配置（以 Map<configKey, configValue> 形式返回，保持插入顺序）
     */
    public Map<String, String> getAllConfigs() {
        List<SysConfig> configs = sysConfigMapper.selectList(
                new LambdaQueryWrapper<SysConfig>()
                        .orderByAsc(SysConfig::getId)
        );

        Map<String, String> result = new LinkedHashMap<>();
        for (SysConfig config : configs) {
            result.put(config.getConfigKey(), config.getConfigValue());
        }
        return result;
    }

    /**
     * 获取所有配置实体（含 description，供管理后台展示）
     */
    public List<SysConfig> getAllConfigEntities() {
        return sysConfigMapper.selectList(
                new LambdaQueryWrapper<SysConfig>()
                        .orderByAsc(SysConfig::getId)
        );
    }

    /**
     * 批量 upsert 配置项
     * 对每个 key：
     *   - 已存在 → 更新 config_value
     *   - 不存在 → 插入新记录
     *
     * @param updates 配置键值对
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateConfigs(Map<String, String> updates) {
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            // 查询是否已存在
            SysConfig existing = sysConfigMapper.selectOne(
                    new LambdaQueryWrapper<SysConfig>()
                            .eq(SysConfig::getConfigKey, key)
            );

            if (existing != null) {
                // 更新已有配置
                existing.setConfigValue(value);
                sysConfigMapper.updateById(existing);
            } else {
                // 插入新配置项
                SysConfig newConfig = new SysConfig();
                newConfig.setConfigKey(key);
                newConfig.setConfigValue(value);
                newConfig.setDescription(key);
                sysConfigMapper.insert(newConfig);
                log.info("[SysConfig] 新增配置项: {} = {}", key, value);
            }
        }
    }
}
