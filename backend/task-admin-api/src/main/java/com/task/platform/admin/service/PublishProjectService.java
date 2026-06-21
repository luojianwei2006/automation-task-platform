package com.task.platform.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.task.platform.admin.dto.publish.CreateProjectReq;
import com.task.platform.admin.dto.publish.UpdateProjectReq;
import com.task.platform.admin.entity.PublishProject;
import com.task.platform.admin.mapper.PublishProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目服务（视频发布功能）
 *
 * @author TaskPlatform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublishProjectService {

    private final PublishProjectMapper publishProjectMapper;

    private static final int STATUS_NORMAL  = 1;
    private static final int STATUS_DELETED = 0;

    /**
     * 创建项目
     */
    @Transactional(rollbackFor = Exception.class)
    public PublishProject create(CreateProjectReq req) {
        PublishProject project = new PublishProject();
        project.setName(req.getName());
        project.setDescription(req.getDescription());
        project.setCoverUrl(req.getCoverUrl());
        project.setStatus(STATUS_NORMAL);
        publishProjectMapper.insert(project);
        log.info("[PublishProject] 创建项目: id={}, name={}", project.getId(), project.getName());
        return project;
    }

    /**
     * 项目列表（分页 + 搜索）
     */
    public IPage<PublishProject> list(int page, int size, String keyword) {
        LambdaQueryWrapper<PublishProject> wrapper = new LambdaQueryWrapper<PublishProject>()
                .eq(PublishProject::getStatus, STATUS_NORMAL)
                .orderByDesc(PublishProject::getCreatedAt);

        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(PublishProject::getName, keyword.trim());
        }

        return publishProjectMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /** 全部正常项目（下拉选择用） */
    public List<PublishProject> listAll() {
        LambdaQueryWrapper<PublishProject> wrapper = new LambdaQueryWrapper<PublishProject>()
                .eq(PublishProject::getStatus, STATUS_NORMAL)
                .orderByDesc(PublishProject::getCreatedAt);
        return publishProjectMapper.selectList(wrapper);
    }

    /**
     * 项目详情
     */
    public PublishProject getById(Long id) {
        PublishProject project = publishProjectMapper.selectById(id);
        if (project == null || project.getStatus() == STATUS_DELETED) {
            return null;
        }
        return project;
    }

    /**
     * 更新项目
     */
    @Transactional(rollbackFor = Exception.class)
    public PublishProject update(Long id, UpdateProjectReq req) {
        PublishProject project = publishProjectMapper.selectById(id);
        if (project == null || project.getStatus() == STATUS_DELETED) {
            return null;
        }

        if (req.getName() != null) {
            project.setName(req.getName());
        }
        if (req.getDescription() != null) {
            project.setDescription(req.getDescription());
        }
        if (req.getCoverUrl() != null) {
            project.setCoverUrl(req.getCoverUrl());
        }

        publishProjectMapper.updateById(project);
        log.info("[PublishProject] 更新项目: id={}", id);
        return project;
    }

    /**
     * 软删除项目
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean softDelete(Long id) {
        PublishProject project = publishProjectMapper.selectById(id);
        if (project == null || project.getStatus() == STATUS_DELETED) {
            return false;
        }
        project.setStatus(STATUS_DELETED);
        publishProjectMapper.updateById(project);
        log.info("[PublishProject] 软删除项目: id={}", id);
        return true;
    }
}
