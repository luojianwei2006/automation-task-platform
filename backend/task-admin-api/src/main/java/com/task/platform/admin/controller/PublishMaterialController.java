package com.task.platform.admin.controller;

import com.task.platform.admin.dto.publish.MaterialListVO;
import com.task.platform.admin.entity.PublishMaterial;
import com.task.platform.admin.service.PublishMaterialService;
import com.task.platform.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 素材管理接口（视频发布功能）
 *
 * @author TaskPlatform
 */
@RestController
@RequestMapping("/publish")
@RequiredArgsConstructor
public class PublishMaterialController {

    private final PublishMaterialService publishMaterialService;
    private final com.task.platform.admin.service.PublishProjectService publishProjectService;

    /**
     * 上传素材
     * POST /api/publish/projects/{projectId}/materials
     */
    @PostMapping(value = "/projects/{projectId}/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PublishMaterial> uploadMaterial(
            @PathVariable Long projectId,
            @RequestParam("type") String type,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "sortOrder", defaultValue = "0") Integer sortOrder,
            @RequestParam(value = "content", required = false) String content) {

        // 文案类型不需要file，图片/音乐/视频类型必须传file
        if (!"text".equals(type) && (file == null || file.isEmpty())) {
            return ApiResponse.error(400, "非文案素材必须上传文件");
        }
        if ("text".equals(type) && (content == null || content.isBlank())) {
            return ApiResponse.error(400, "文案素材的content字段不能为空");
        }

        PublishMaterial material = publishMaterialService.upload(projectId, file, type, title, sortOrder, content);
        return ApiResponse.success(material, "素材上传成功");
    }

    /**
     * 素材列表（按type筛选、按sort_order排序）
     * GET /api/publish/projects/{projectId}/materials?type=
     */
    @GetMapping("/projects/{projectId}/materials")
    public ApiResponse<List<MaterialListVO>> listMaterials(
            @PathVariable Long projectId,
            @RequestParam(required = false) String type) {

        List<PublishMaterial> list = publishMaterialService.listByProject(projectId, type);
        String projectName = getProjectName(projectId);
        List<MaterialListVO> vos = list.stream().map(m -> toVO(m, projectName)).collect(Collectors.toList());
        return ApiResponse.success(vos);
    }

    /**
     * 素材列表（支持projectId作为查询参数，对齐前端调用）
     * GET /api/publish/materials?projectId=1&type=text&page=1&size=20
     */
    @GetMapping("/materials")
    public ApiResponse<Map<String, Object>> listMaterialsByQuery(
            @RequestParam Long projectId,
            @RequestParam(required = false) String type) {

        List<PublishMaterial> list = publishMaterialService.listByProject(projectId, type);
        String projectName = getProjectName(projectId);
        List<MaterialListVO> vos = list.stream().map(m -> toVO(m, projectName)).collect(Collectors.toList());
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("records", vos);
        result.put("total", vos.size());
        result.put("page", 1);
        result.put("size", vos.size());
        return ApiResponse.success(result);
    }

    /**
     * 下载素材文件
     * GET /api/publish/materials/{id}/download
     */
    @GetMapping("/materials/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        PublishMaterial material = publishMaterialService.getById(id);
        if (material == null || material.getFileUrl() == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            String fileUrl = material.getFileUrl();
            // 将相对URL转为绝对路径
            String basePath = System.getProperty("user.dir");
            java.nio.file.Path filePath = Paths.get(basePath, fileUrl);
            Resource resource = new FileSystemResource(filePath);

            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            String filename = material.getTitle() != null ? material.getTitle() : filePath.getFileName().toString();
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + java.net.URLEncoder.encode(filename, "UTF-8") + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 软删除素材（进回收站）
     * DELETE /api/publish/materials/{id}
     */
    @DeleteMapping("/materials/{id}")
    public ApiResponse<Void> deleteMaterial(@PathVariable Long id,
                                             @RequestParam(defaultValue = "0") Long deletedBy) {
        boolean ok = publishMaterialService.softDelete(id, deletedBy);
        if (!ok) {
            return ApiResponse.error(404, "素材不存在或已删除");
        }
        return ApiResponse.success(null, "素材已移入回收站");
    }

    /**
     * 创建文案素材（JSON body，对齐前端调用）
     * POST /api/publish/materials/text
     */
    @PostMapping("/materials/text")
    public ApiResponse<PublishMaterial> createTextMaterial(@RequestBody CreateTextReq req) {
        if (req.getContent() == null || req.getContent().isBlank()) {
            return ApiResponse.error(400, "文案内容不能为空");
        }
        PublishMaterial material = publishMaterialService.upload(
                req.getProjectId(), null, "text", req.getTitle(), 0, req.getContent());
        return ApiResponse.success(material, "文案保存成功");
    }

    // ==================== helper ====================

    private String getProjectName(Long projectId) {
        if (projectId == null) return "";
        try {
            var project = publishProjectService.getById(projectId);
            return project != null ? project.getName() : "";
        } catch (Exception e) {
            return "";
        }
    }

    // ==================== DTO 转换 ====================

    private MaterialListVO toVO(PublishMaterial m, String projectName) {
        MaterialListVO vo = toVO(m);
        vo.setProjectName(projectName);
        return vo;
    }

    private MaterialListVO toVO(PublishMaterial m) {
        MaterialListVO vo = new MaterialListVO();
        vo.setId(m.getId());
        vo.setProjectId(m.getProjectId());
        vo.setType(m.getType());
        vo.setTitle(m.getTitle());
        vo.setFileUrl(m.getFileUrl());
        vo.setFileSize(m.getFileSize());
        vo.setContent(m.getContent());
        vo.setDuration(m.getDuration());
        vo.setResolution(m.getResolution());
        vo.setSortOrder(m.getSortOrder());
        vo.setCreatedAt(m.getCreatedAt());
        return vo;
    }

    /** 创建文案请求体 */
    @lombok.Data
    public static class CreateTextReq {
        private Long projectId;
        private String title;
        private String content;
    }
}
