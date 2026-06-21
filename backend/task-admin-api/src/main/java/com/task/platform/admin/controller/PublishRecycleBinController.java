package com.task.platform.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.task.platform.admin.entity.PublishMaterial;
import com.task.platform.admin.entity.PublishRecycleBin;
import com.task.platform.admin.service.PublishRecycleBinService;
import com.task.platform.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 回收站接口（视频发布功能）
 *
 * @author TaskPlatform
 */
@RestController
@RequestMapping("/publish/recycle-bin")
@RequiredArgsConstructor
public class PublishRecycleBinController {

    private final PublishRecycleBinService publishRecycleBinService;
    private final com.task.platform.admin.service.PublishProjectService publishProjectService;
    private final ObjectMapper objectMapper;

    /**
     * 回收站列表
     * GET /api/publish/recycle-bin?page=1&size=20
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        IPage<PublishRecycleBin> result = publishRecycleBinService.list(page, size);
        List<Map<String, Object>> records = result.getRecords().stream()
                .map(this::toRecycleVO).collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("total", result.getTotal());
        data.put("page", page);
        data.put("size", size);
        data.put("records", records);
        return ApiResponse.success(data);
    }

    /** 将 PublishRecycleBin 转为前端需要的 VO（解析 dataJson 提取关键字段） */
    private Map<String, Object> toRecycleVO(PublishRecycleBin bin) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", bin.getId());
        vo.put("deletedAt", bin.getDeletedAt());

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> snap = objectMapper.readValue(bin.getDataJson(), Map.class);
            vo.put("type", snap.getOrDefault("type", ""));
            vo.put("title", snap.getOrDefault("title", ""));
            Object projectIdObj = snap.get("projectId");
            if (projectIdObj != null) {
                Long projectId = ((Number) projectIdObj).longValue();
                try {
                    var project = publishProjectService.getById(projectId);
                    vo.put("projectName", project != null ? project.getName() : "");
                } catch (Exception e) {
                    vo.put("projectName", "");
                }
            } else {
                vo.put("projectName", "");
            }
        } catch (Exception e) {
            vo.put("type", "");
            vo.put("title", "");
            vo.put("projectName", "");
        }
        return vo;
    }

    /**
     * 从回收站恢复素材
     * POST /api/publish/recycle-bin/{id}/restore
     */
    @PostMapping("/{id}/restore")
    public ApiResponse<Void> restore(@PathVariable Long id) {
        boolean ok = publishRecycleBinService.restore(id);
        if (!ok) {
            return ApiResponse.error(404, "回收站记录不存在或已恢复");
        }
        return ApiResponse.success(null, "素材已恢复");
    }

    /**
     * 彻底删除
     * DELETE /api/publish/recycle-bin/{id}
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> permanentDelete(@PathVariable Long id) {
        boolean ok = publishRecycleBinService.permanentDelete(id);
        if (!ok) {
            return ApiResponse.error(404, "回收站记录不存在");
        }
        return ApiResponse.success(null, "已彻底删除");
    }
}
