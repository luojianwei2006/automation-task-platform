package com.task.platform.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.task.platform.admin.entity.PublishMaterial;
import com.task.platform.admin.entity.PublishRecycleBin;
import com.task.platform.admin.mapper.PublishMaterialMapper;
import com.task.platform.admin.mapper.PublishRecycleBinMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 素材服务（视频发布功能）
 *
 * @author TaskPlatform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublishMaterialService {

    private final PublishMaterialMapper publishMaterialMapper;
    private final PublishRecycleBinMapper publishRecycleBinMapper;
    private final ObjectMapper objectMapper;

    /** 上传根目录（对齐 StaticResourceConfig 的映射路径） */
    private static final String UPLOAD_ROOT = "/Users/luojianwei/Documents/Workbuddy/automation_project/uploads/publish/";

    /**
     * 上传素材文件
     *
     * @param projectId  关联项目ID
     * @param file       上传文件
     * @param type       素材类型：text/image/music/video
     * @param title      素材标题
     * @param sortOrder  段落序号
     * @param content    文案内容（type=text时使用）
     * @return 创建的素材记录
     */
    @Transactional(rollbackFor = Exception.class)
    public PublishMaterial upload(Long projectId, MultipartFile file, String type,
                                  String title, Integer sortOrder, String content) {
        PublishMaterial material = new PublishMaterial();
        material.setProjectId(projectId);
        material.setType(type);
        material.setTitle(title);
        material.setSortOrder(sortOrder != null ? sortOrder : 0);

        if ("text".equals(type)) {
            // 文本素材：直接存content，不需要文件
            material.setContent(content);
            material.setFileUrl(""); // 文本无文件，设空串避免MySQL NOT NULL报错
            material.setFileSize((long) (content != null ? content.getBytes().length : 0));
        } else if (file != null && !file.isEmpty()) {
            // 文件素材：存到 upload-service 路径
            String fileUrl = saveFile(file);
            material.setFileUrl(fileUrl);
            material.setFileSize(file.getSize());
        } else {
            material.setFileUrl(""); // 兜底
        }

        material.setDeleted(0);
        publishMaterialMapper.insert(material);
        log.info("[PublishMaterial] 上传素材: id={}, projectId={}, type={}", material.getId(), projectId, type);
        return material;
    }

    /**
     * 素材列表（按type筛选、按sort_order排序）
     */
    public List<PublishMaterial> listByProject(Long projectId, String type) {
        LambdaQueryWrapper<PublishMaterial> wrapper = new LambdaQueryWrapper<PublishMaterial>()
                .eq(PublishMaterial::getProjectId, projectId)
                .eq(PublishMaterial::getDeleted, 0)
                .orderByAsc(PublishMaterial::getSortOrder)
                .orderByAsc(PublishMaterial::getCreatedAt);

        if (type != null && !type.isBlank()) {
            wrapper.eq(PublishMaterial::getType, type.trim());
        }

        return publishMaterialMapper.selectList(wrapper);
    }

    /**
     * 获取素材（用于下载）
     */
    public PublishMaterial getById(Long id) {
        PublishMaterial material = publishMaterialMapper.selectById(id);
        if (material == null || material.getDeleted() == 1) {
            return null;
        }
        return material;
    }

    /**
     * 软删除素材（进回收站）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean softDelete(Long id, Long deletedBy) {
        PublishMaterial material = publishMaterialMapper.selectById(id);
        if (material == null || material.getDeleted() == 1) {
            return false;
        }

        // 1. 硬更新 deleted=1（不用updateById，避免MyBatis-Plus字段策略干扰）
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PublishMaterial> uw =
            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        uw.eq(PublishMaterial::getId, id).set(PublishMaterial::getDeleted, 1);
        publishMaterialMapper.update(new PublishMaterial(), uw);

        // 2. 插入 recycle_bin 快照
        try {
            String dataJson = objectMapper.writeValueAsString(material);
            PublishRecycleBin bin = new PublishRecycleBin();
            bin.setOriginalTable("t_material");
            bin.setOriginalId(id);
            bin.setDataJson(dataJson);
            bin.setDeletedBy(deletedBy);
            bin.setDeletedAt(LocalDateTime.now());
            bin.setRestored(0);
            bin.setExpiredAt(LocalDateTime.now().plusDays(30));
            publishRecycleBinMapper.insert(bin);
            log.info("[PublishMaterial] 软删除素材进回收站: id={}, recycleBinId={}", id, bin.getId());
        } catch (Exception e) {
            log.error("[PublishMaterial] 回收站快照写入失败: id={}", id, e);
            throw new RuntimeException("回收站快照写入失败", e);
        }

        return true;
    }

    // ==================== 私有工具 ====================

    /**
     * 保存上传文件到磁盘，返回访问URL
     */
    private String saveFile(MultipartFile file) {
        try {
            Path uploadDir = Paths.get(UPLOAD_ROOT);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            String originalName = file.getOriginalFilename();
            String ext = "";
            if (originalName != null && originalName.contains(".")) {
                ext = originalName.substring(originalName.lastIndexOf("."));
            }
            String storedName = UUID.randomUUID().toString().replace("-", "") + ext;
            Path targetPath = uploadDir.resolve(storedName);
            file.transferTo(targetPath.toFile());

            return "/uploads/publish/" + storedName;
        } catch (IOException e) {
            log.error("[PublishMaterial] 文件保存失败", e);
            throw new RuntimeException("文件保存失败", e);
        }
    }
}
