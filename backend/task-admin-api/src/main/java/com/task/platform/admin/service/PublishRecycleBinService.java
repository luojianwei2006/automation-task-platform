package com.task.platform.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.task.platform.admin.entity.PublishMaterial;
import com.task.platform.admin.entity.PublishRecycleBin;
import com.task.platform.admin.mapper.PublishMaterialMapper;
import com.task.platform.admin.mapper.PublishRecycleBinMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 回收站服务（视频发布功能）
 *
 * @author TaskPlatform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublishRecycleBinService {

    private final PublishRecycleBinMapper publishRecycleBinMapper;
    private final PublishMaterialMapper publishMaterialMapper;
    private final ObjectMapper objectMapper;

    /**
     * 回收站列表（分页）
     */
    public IPage<PublishRecycleBin> list(int page, int size) {
        LambdaQueryWrapper<PublishRecycleBin> wrapper = new LambdaQueryWrapper<PublishRecycleBin>()
                .eq(PublishRecycleBin::getRestored, 0)
                .orderByDesc(PublishRecycleBin::getDeletedAt);

        return publishRecycleBinMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /**
     * 从回收站恢复素材
     */
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public boolean restore(Long id) {
        PublishRecycleBin bin = publishRecycleBinMapper.selectById(id);
        if (bin == null || bin.getRestored() == 1) {
            return false;
        }

        try {
            // 解析快照JSON，还原字段（只还原关键字段）
            Map<String, Object> dataMap = objectMapper.readValue(bin.getDataJson(), Map.class);

            // 恢复 t_material 的 deleted 标记（直接SQL，绕过MyBatis-Plus包装器）
            publishMaterialMapper.updateDeleted(bin.getOriginalId(), 0);

            // 标记回收站记录已恢复
            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PublishRecycleBin> rbw =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
            rbw.eq(PublishRecycleBin::getId, id).set(PublishRecycleBin::getRestored, 1);
            publishRecycleBinMapper.update(null, rbw);

            log.info("[PublishRecycleBin] 恢复素材: recycleBinId={}, originalId={}", id, bin.getOriginalId());
            return true;
        } catch (Exception e) {
            log.error("[PublishRecycleBin] 恢复失败: id={}", id, e);
            throw new RuntimeException("恢复失败", e);
        }
    }

    /**
     * 彻底删除（从回收站清除 + 删除磁盘文件）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean permanentDelete(Long id) {
        PublishRecycleBin bin = publishRecycleBinMapper.selectById(id);
        if (bin == null) {
            return false;
        }

        // 删除原始记录
        PublishMaterial material = publishMaterialMapper.selectById(bin.getOriginalId());
        if (material != null) {
            publishMaterialMapper.deleteById(material.getId());
        }

        // 删除回收站记录
        publishRecycleBinMapper.deleteById(id);

        log.info("[PublishRecycleBin] 彻底删除: recycleBinId={}, originalId={}", id, bin.getOriginalId());
        return true;
    }
}
