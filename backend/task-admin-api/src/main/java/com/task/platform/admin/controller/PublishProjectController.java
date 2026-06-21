package com.task.platform.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.task.platform.admin.dto.publish.CreateProjectReq;
import com.task.platform.admin.dto.publish.UpdateProjectReq;
import com.task.platform.admin.entity.PublishProject;
import com.task.platform.admin.service.PublishProjectService;
import com.task.platform.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 项目管理接口（视频发布功能）
 *
 * @author TaskPlatform
 */
@RestController
@RequestMapping("/publish/projects")
@RequiredArgsConstructor
public class PublishProjectController {

    private final PublishProjectService publishProjectService;

    /**
     * 创建项目
     * POST /api/publish/projects
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody CreateProjectReq req) {
        if (req.getName() == null || req.getName().isBlank()) {
            return ApiResponse.error(400, "项目名称不能为空");
        }
        PublishProject project = publishProjectService.create(req);
        Map<String, Object> data = new HashMap<>();
        data.put("id", project.getId());
        data.put("name", project.getName());
        return ApiResponse.success(data, "项目创建成功");
    }

    /**
     * 项目列表（分页 + 搜索）
     * GET /api/publish/projects?page=1&size=20&keyword=
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {

        IPage<PublishProject> result = publishProjectService.list(page, size, keyword);
        Map<String, Object> data = new HashMap<>();
        data.put("total", result.getTotal());
        data.put("page", page);
        data.put("size", size);
        data.put("records", result.getRecords());
        return ApiResponse.success(data);
    }

    /**
     * 全部项目（下拉选择用，不分页）
     * GET /api/publish/projects/all
     */
    @GetMapping("/all")
    public ApiResponse<List<PublishProject>> all() {
        return ApiResponse.success(publishProjectService.listAll());
    }

    /**
     * 项目详情
     * GET /api/publish/projects/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<PublishProject> getById(@PathVariable Long id) {
        PublishProject project = publishProjectService.getById(id);
        if (project == null) {
            return ApiResponse.error(404, "项目不存在或已删除");
        }
        return ApiResponse.success(project);
    }

    /**
     * 更新项目
     * PUT /api/publish/projects/{id}
     */
    @PutMapping("/{id}")
    public ApiResponse<PublishProject> update(@PathVariable Long id,
                                               @RequestBody UpdateProjectReq req) {
        PublishProject project = publishProjectService.update(id, req);
        if (project == null) {
            return ApiResponse.error(404, "项目不存在或已删除");
        }
        return ApiResponse.success(project, "项目已更新");
    }

    /**
     * 软删除项目
     * DELETE /api/publish/projects/{id}
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        boolean ok = publishProjectService.softDelete(id);
        if (!ok) {
            return ApiResponse.error(404, "项目不存在或已删除");
        }
        return ApiResponse.success(null, "项目已删除");
    }
}
