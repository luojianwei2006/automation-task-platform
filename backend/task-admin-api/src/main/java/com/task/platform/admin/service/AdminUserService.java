package com.task.platform.admin.service;

import com.task.platform.admin.entity.AppUser;
import com.task.platform.admin.mapper.AppUserMapper;
import com.task.platform.common.exception.BusinessException;
import com.task.platform.common.response.ErrorCode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 管理后台 - 用户管理服务（含实名认证审核）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final AppUserMapper appUserMapper;

    // 实名认证状态常量
    public static final int STATUS_UNAUTHENTICATED = 0; // 未认证
    public static final int STATUS_PENDING         = 1; // 审核中
    public static final int STATUS_PASSED          = 2; // 已认证
    public static final int STATUS_FAILED          = 3; // 失败

    /**
     * 查询用户详情
     */
    public AppUser getUserById(Long userId) {
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    /**
     * 查询实名认证状态
     */
    public RealAuthStatusVO getRealAuthStatus(Long userId) {
        AppUser user = getUserById(userId);
        RealAuthStatusVO vo = new RealAuthStatusVO();
        vo.setStatus(user.getRealAuthStatus() != null ? user.getRealAuthStatus() : STATUS_UNAUTHENTICATED);
        vo.setRealName(user.getRealName());
        // 身份证脱敏：保留前6位和后4位
        String idCard = user.getIdCard();
        if (idCard != null && idCard.length() > 11 && idCard.startsWith("[ENCRYPTED]")) {
            idCard = idCard.substring(11);
        }
        vo.setIdCardMasked(maskIdCard(idCard));
        vo.setStatusDesc(getStatusDesc(vo.getStatus()));
        return vo;
    }

    /**
     * 实名认证审核（通过 / 拒绝）
     *
     * @param userId  被审核的用户ID
     * @param pass    true=通过, false=拒绝
     * @param reason  拒绝原因（pass=false 时必填）
     */
    public void reviewRealAuth(Long userId, boolean pass, String reason) {
        AppUser user = getUserById(userId);

        if (STATUS_PENDING != user.getRealAuthStatus()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前用户不处于审核中状态");
        }

        if (pass) {
            user.setRealAuthStatus(STATUS_PASSED);
            log.info("[Admin-RealAuth] 用户 {} 实名认证审核通过", userId);
            // TODO: 发放实名认证奖励（0.5元）
            // TODO: 发送站内消息通知用户
        } else {
            if (reason == null || reason.isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "拒绝时必须填写原因");
            }
            user.setRealAuthStatus(STATUS_FAILED);
            log.info("[Admin-RealAuth] 用户 {} 实名认证审核拒绝，原因: {}", userId, reason);
            // TODO: 发送站内消息通知用户，附带拒绝原因
        }

        appUserMapper.updateById(user);
    }

    /**
     * 封禁/解封用户
     */
    public void toggleUserStatus(Long userId, boolean enable) {
        AppUser user = getUserById(userId);
        user.setStatus(enable ? 1 : 0);
        appUserMapper.updateById(user);
    }

    // ==================== VO ====================

    @Data
    public static class RealAuthStatusVO {
        private Integer status;
        private String statusDesc;
        private String realName;
        private String idCardMasked;
    }

    // ==================== 私有工具 ====================

    private String getStatusDesc(Integer status) {
        if (status == null) return "未认证";
        return switch (status) {
            case STATUS_UNAUTHENTICATED -> "未认证";
            case STATUS_PENDING         -> "审核中，预计1-2个工作日内完成";
            case STATUS_PASSED          -> "已认证";
            case STATUS_FAILED          -> "认证失败，请重新提交";
            default                     -> "未知状态";
        };
    }

    /**
     * 身份证脱敏：保留前6位和后4位
     */
    private String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() != 18) return idCard;
        return idCard.substring(0, 6) + "********" + idCard.substring(14);
    }
}
