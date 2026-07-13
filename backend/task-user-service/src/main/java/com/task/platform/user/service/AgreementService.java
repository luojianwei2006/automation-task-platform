package com.task.platform.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.task.platform.common.entity.Agreement;
import com.task.platform.common.mapper.AgreementMapper;
import com.task.platform.common.vo.AgreementVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * 协议文档服务（读侧，匿名公开）
 * 提供按 type 查询协议的能力，供安卓 WebView 展示。
 *
 * @author TaskPlatform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgreementService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final List<String> VALID_TYPES = Arrays.asList("about", "privacy", "register");

    private final AgreementMapper agreementMapper;

    /**
     * 判断协议类型是否合法（about / privacy / register）
     */
    public boolean isValidType(String type) {
        return type != null && VALID_TYPES.contains(type);
    }

    /**
     * 按 type 读取协议。
     * 合法但无记录时返回 contentHtml 为空的 VO（友好空态，WebView 展示占位）。
     */
    public AgreementVO getByType(String type) {
        Agreement entity = agreementMapper.selectOne(
                new LambdaQueryWrapper<Agreement>().eq(Agreement::getType, type)
        );

        if (entity == null) {
            return AgreementVO.builder()
                    .type(type)
                    .title("")
                    .contentHtml("")
                    .version(0)
                    .updatedAt("")
                    .build();
        }

        return AgreementVO.builder()
                .type(entity.getType())
                .title(entity.getTitle())
                .contentHtml(entity.getContentHtml() == null ? "" : entity.getContentHtml())
                .version(entity.getVersion())
                .updatedAt(entity.getUpdatedAt() == null ? "" : entity.getUpdatedAt().format(FORMATTER))
                .build();
    }
}
