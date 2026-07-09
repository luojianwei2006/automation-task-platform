package com.task.platform.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.task.platform.admin.dto.publish.CreateProjectReq;
import com.task.platform.admin.dto.publish.UpdateProjectReq;
import com.task.platform.admin.entity.Merchant;
import com.task.platform.admin.entity.PublishProject;
import com.task.platform.admin.mapper.MerchantMapper;
import com.task.platform.admin.mapper.PublishProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    private final MerchantMapper merchantMapper;

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
        project.setMerchantId(req.getMerchantId());
        project.setStatus(STATUS_NORMAL);
        publishProjectMapper.insert(project);
        log.info("[PublishProject] 创建项目: id={}, name={}", project.getId(), project.getName());
        return project;
    }

    /**
     * 项目列表（分页 + 搜索）
     */
    public IPage<PublishProject> list(int page, int size, String keyword) {
        return list(page, size, keyword, null);
    }

    public IPage<PublishProject> list(int page, int size, String keyword, Long merchantId) {
        LambdaQueryWrapper<PublishProject> wrapper = new LambdaQueryWrapper<PublishProject>()
                .eq(PublishProject::getStatus, STATUS_NORMAL)
                .orderByDesc(PublishProject::getCreatedAt);

        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(PublishProject::getName, keyword.trim());
        }
        if (merchantId != null && merchantId > 0) {
            wrapper.eq(PublishProject::getMerchantId, merchantId);
        }

        return publishProjectMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /** 全部正常项目（下拉选择用） */
    public List<PublishProject> listAll() {
        return listAll(null);
    }

    public List<PublishProject> listAll(Long merchantId) {
        LambdaQueryWrapper<PublishProject> wrapper = new LambdaQueryWrapper<PublishProject>()
                .eq(PublishProject::getStatus, STATUS_NORMAL)
                .orderByDesc(PublishProject::getCreatedAt);
        if (merchantId != null && merchantId > 0) {
            wrapper.eq(PublishProject::getMerchantId, merchantId);
        }
        List<PublishProject> projects = publishProjectMapper.selectList(wrapper);
        fillServiceFeeRate(projects);
        return projects;
    }

    /**
     * 项目详情
     */
    public PublishProject getById(Long id) {
        PublishProject project = publishProjectMapper.selectById(id);
        if (project == null || project.getStatus() == STATUS_DELETED) {
            return null;
        }
        fillServiceFeeRate(List.of(project));
        return project;
    }

    /**
     * 回填项目所属商户的服务费率（前端费率联动用）。
     * 平台项目（merchantId 为空）或商户不存在时 serviceFeeRate 保持 null，由前端兜底为默认 0.15。
     */
    private void fillServiceFeeRate(List<PublishProject> projects) {
        if (projects == null || projects.isEmpty()) {
            return;
        }
        for (PublishProject project : projects) {
            if (project.getMerchantId() != null) {
                Merchant merchant = merchantMapper.selectById(project.getMerchantId());
                if (merchant != null) {
                    project.setServiceFeeRate(merchant.getServiceFeeRate());
                }
            }
        }
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
        // merchantId 明确传 null 表示平台项目，传 >0 表示商户项目
        if (req.getMerchantId() != null) {
            project.setMerchantId(req.getMerchantId() > 0 ? req.getMerchantId() : null);
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
