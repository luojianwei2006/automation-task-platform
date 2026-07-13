package com.task.platform.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.task.platform.admin.dto.AgreementSaveReq;
import com.task.platform.common.entity.Agreement;
import com.task.platform.common.mapper.AgreementMapper;
import com.task.platform.common.vo.AgreementVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 协议文档服务（写侧）
 * 负责按 type upsert t_agreement：存在则 version 自增并刷新更新时间/操作人，不存在则插入（version=1）。
 *
 * @author TaskPlatform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgreementService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AgreementMapper agreementMapper;

    /**
     * 按 type 查询当前协议（用于编辑回填）。
     *
     * @param type 协议类型
     * @return 协议 VO；无记录时返回 null（前端展示空态）
     */
    public AgreementVO getByType(String type) {
        Agreement entity = agreementMapper.selectOne(
                new LambdaQueryWrapper<Agreement>().eq(Agreement::getType, type)
        );
        return toVO(entity);
    }

    /**
     * 保存（upsert）协议。
     *
     * @param req     请求体（type/title/contentHtml）
     * @param operator 操作人（来自网关注入的 X-User-Id，缺失时落 "admin"）
     * @return 保存后的协议 VO（含最新 version 与格式化 updatedAt）
     */
    @Transactional(rollbackFor = Exception.class)
    public AgreementVO save(AgreementSaveReq req, String operator) {
        Agreement existing = agreementMapper.selectOne(
                new LambdaQueryWrapper<Agreement>().eq(Agreement::getType, req.getType())
        );

        Agreement entity;
        if (existing != null) {
            existing.setTitle(req.getTitle());
            existing.setContentHtml(req.getContentHtml());
            int nextVersion = (existing.getVersion() == null ? 0 : existing.getVersion()) + 1;
            existing.setVersion(nextVersion);
            existing.setUpdatedBy(operator);
            existing.setUpdatedAt(LocalDateTime.now());
            agreementMapper.updateById(existing);
            entity = existing;
            log.info("[Agreement] 更新协议 type={}, newVersion={}, operator={}", req.getType(), nextVersion, operator);
        } else {
            entity = new Agreement();
            entity.setType(req.getType());
            entity.setTitle(req.getTitle());
            entity.setContentHtml(req.getContentHtml());
            entity.setVersion(1);
            entity.setUpdatedBy(operator);
            entity.setUpdatedAt(LocalDateTime.now());
            agreementMapper.insert(entity);
            log.info("[Agreement] 新增协议 type={}, operator={}", req.getType(), operator);
        }
        return toVO(entity);
    }

    /**
     * 实体 -> VO（updatedAt 格式化为字符串，避免各端 LocalDateTime 序列化差异）
     */
    private AgreementVO toVO(Agreement entity) {
        if (entity == null) {
            return null;
        }
        return AgreementVO.builder()
                .type(entity.getType())
                .title(entity.getTitle())
                .contentHtml(entity.getContentHtml())
                .version(entity.getVersion())
                .updatedAt(entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().format(FORMATTER))
                .build();
    }
}
