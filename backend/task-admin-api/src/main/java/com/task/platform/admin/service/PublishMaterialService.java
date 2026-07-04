package com.task.platform.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.task.platform.admin.dto.publish.MaterialListVO;
import com.task.platform.admin.dto.publish.PublishMaterialPreviewVO;
import com.task.platform.admin.dto.publish.VideoGroupVO;
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
import java.util.*;
import java.util.stream.Collectors;

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

    /** 刷新限频缓存：key=userId:projectId，value=上次刷新时间戳(ms) */
    private final Map<String, Long> lastRefreshMap = new HashMap<>();

    /** 随机素材结果缓存：key=userId:projectId，value=上次选中的结果 */
    private final Map<String, PublishMaterialPreviewVO> cacheMap = new HashMap<>();

    private final Random random = new Random();

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

        // 1. 硬更新 deleted=1（直接SQL，绕过MyBatis-Plus包装器）
        publishMaterialMapper.updateDeleted(id, 1);

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

    // ==================== 随机素材预览（移动端） ====================

    /**
     * 获取项目随机素材供移动端预览。
     * <p>
     * 限频规则：同一用户+同一项目 60 秒内返回缓存结果，超出后才重新随机选取。
     *
     * @param projectId 项目ID
     * @param userId    用户ID（用于限频）
     */
    public synchronized PublishMaterialPreviewVO getRandomPreview(Long projectId, Long userId) {
        String key = userId + ":" + projectId;
        long now = System.currentTimeMillis();
        Long lastRefresh = lastRefreshMap.get(key);

        // 限频内：返回缓存
        if (lastRefresh != null && now - lastRefresh < 60_000) {
            log.debug("[PublishMaterial] 限频命中，返回缓存结果: key={}", key);
            return cacheMap.get(key);
        }

        // 重新随机选取
        PublishMaterialPreviewVO vo = new PublishMaterialPreviewVO();

        // 1. 文案：type=text 随机取 1 条
        List<PublishMaterial> texts = listByProject(projectId, "text");
        vo.setTextMaterial(texts.isEmpty() ? null : toListVO(texts.get(random.nextInt(texts.size()))));

        // 2. 图片：type=image 随机取 1 条
        List<PublishMaterial> images = listByProject(projectId, "image");
        vo.setImageMaterial(images.isEmpty() ? null : toListVO(images.get(random.nextInt(images.size()))));

        // 3. 音乐：type=music 随机取 1 条
        List<PublishMaterial> musics = listByProject(projectId, "music");
        vo.setMusicMaterial(musics.isEmpty() ? null : toListVO(musics.get(random.nextInt(musics.size()))));

        // 4. 视频：按 sortOrder 分组，每组随机取 1 条
        List<PublishMaterial> videos = listByProject(projectId, "video");
        Map<Integer, List<PublishMaterial>> videoGroupMap = videos.stream()
                .collect(Collectors.groupingBy(PublishMaterial::getSortOrder));
        List<VideoGroupVO> videoGroups = new ArrayList<>();
        for (Map.Entry<Integer, List<PublishMaterial>> entry : videoGroupMap.entrySet()) {
            List<PublishMaterial> group = entry.getValue();
            PublishMaterial randomVideo = group.get(random.nextInt(group.size()));
            VideoGroupVO vg = new VideoGroupVO();
            vg.setSortOrder(entry.getKey());
            vg.setVideo(toListVO(randomVideo));
            videoGroups.add(vg);
        }
        // 按 sortOrder 排序
        videoGroups.sort((a, b) -> a.getSortOrder() - b.getSortOrder());
        vo.setVideoGroups(videoGroups);

        // 更新缓存和时间戳
        lastRefreshMap.put(key, now);
        cacheMap.put(key, vo);

        log.info("[PublishMaterial] 随机素材预览生成: projectId={}, userId={}, text={}, image={}, music={}, videoGroups={}",
                projectId, userId,
                vo.getTextMaterial() != null ? vo.getTextMaterial().getId() : null,
                vo.getImageMaterial() != null ? vo.getImageMaterial().getId() : null,
                vo.getMusicMaterial() != null ? vo.getMusicMaterial().getId() : null,
                videoGroups.size());
        return vo;
    }

    private MaterialListVO toListVO(PublishMaterial m) {
        MaterialListVO v = new MaterialListVO();
        v.setId(m.getId());
        v.setProjectId(m.getProjectId());
        v.setType(m.getType());
        v.setTitle(m.getTitle());
        v.setFileUrl(m.getFileUrl());
        v.setFileSize(m.getFileSize());
        v.setContent(m.getContent());
        v.setDuration(m.getDuration());
        v.setResolution(m.getResolution());
        v.setSortOrder(m.getSortOrder());
        v.setCreatedAt(m.getCreatedAt());
        return v;
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
