package com.task.platform.user.service;

import com.task.platform.common.exception.BusinessException;
import com.task.platform.common.response.ErrorCode;
import com.task.platform.user.entity.User;
import com.task.platform.user.mapper.UserMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 实名认证服务
 *
 * 认证状态流转：
 *   0 未认证 → 提交申请 → 1 审核中
 *   1 审核中 → 自动/人工审核 → 2 已认证 | 3 认证失败
 *   3 认证失败 → 重新提交 → 1 审核中
 *
 * @author TaskPlatform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealAuthService {

    private final UserMapper userMapper;

    // 实名认证状态常量
    public static final int STATUS_UNAUTHENTICATED = 0; // 未认证
    public static final int STATUS_PENDING         = 1; // 审核中
    public static final int STATUS_PASSED          = 2; // 已认证
    public static final int STATUS_FAILED          = 3; // 认证失败

    // ==================== 核心方法 ====================

    /**
     * 提交实名认证申请
     * 
     * @param userId    用户ID
     * @param req       认证请求（姓名+身份证号+照片URL）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitRealAuth(Long userId, RealAuthRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 已通过认证，不可重复提交
        if (STATUS_PASSED == user.getRealAuthStatus()) {
            throw new BusinessException(ErrorCode.REAL_AUTH_ALREADY_PASSED);
        }

        // 审核中，不可重复提交
        if (STATUS_PENDING == user.getRealAuthStatus()) {
            throw new BusinessException(ErrorCode.REAL_AUTH_PENDING);
        }

        // 基本格式校验：身份证18位
        if (!isValidIdCard(req.getIdCard())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "身份证号格式不正确");
        }

        // 对身份证进行AES加密后存储
        // TODO: 接入真实AES加密工具（密钥从配置中心获取，不允许硬编码）
        String encryptedIdCard = encryptIdCard(req.getIdCard());

        user.setRealName(req.getRealName());
        user.setIdCard(encryptedIdCard);
        user.setIdCardFrontUrl(req.getIdCardFrontUrl());
        user.setIdCardBackUrl(req.getIdCardBackUrl());
        user.setHoldIdCardUrl(req.getHoldIdCardUrl());
        user.setRealAuthStatus(STATUS_PENDING);

        userMapper.updateById(user);

        // TODO: 调用腾讯云实人认证API进行自动审核
        // 当前版本：提交后进入"审核中"状态，由运营人员在管理后台手动审核
        log.info("[RealAuth] 用户 {} 提交实名认证申请，姓名: {}，正在等待审核",
                userId, req.getRealName());
    }

    /**
     * 查询实名认证状态
     */
    public RealAuthStatusVO getAuthStatus(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        RealAuthStatusVO vo = new RealAuthStatusVO();
        vo.setStatus(user.getRealAuthStatus() != null ? user.getRealAuthStatus() : STATUS_UNAUTHENTICATED);
        vo.setRealName(user.getRealName());
        // 身份证脱敏展示：仅显示前4位和后4位
        vo.setIdCardMasked(maskIdCard(decryptIdCard(user.getIdCard())));
        vo.setStatusDesc(getStatusDesc(vo.getStatus()));
        return vo;
    }

    /**
     * 管理员审核实名认证（通过 or 拒绝）
     * 此方法供管理后台API调用
     *
     * @param userId    被审核用户ID
     * @param pass      true=通过 false=拒绝
     * @param reason    拒绝原因（pass=false时必填）
     */
    @Transactional(rollbackFor = Exception.class)
    public void reviewRealAuth(Long userId, boolean pass, String reason) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (STATUS_PENDING != user.getRealAuthStatus()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前用户不处于审核中状态");
        }

        if (pass) {
            user.setRealAuthStatus(STATUS_PASSED);
            log.info("[RealAuth] 用户 {} 实名认证审核通过", userId);
            // TODO: 发放实名认证奖励（0.5元）
            // TODO: 发送站内消息通知用户
        } else {
            if (reason == null || reason.isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "拒绝时必须填写原因");
            }
            user.setRealAuthStatus(STATUS_FAILED);
            log.info("[RealAuth] 用户 {} 实名认证审核拒绝，原因: {}", userId, reason);
            // TODO: 发送站内消息通知用户，附带拒绝原因
        }

        // 持久化审核备注与时间（C端无审核人上下文，revieweredBy 留空）
        user.setRealAuthRemark(reason);
        user.setRealAuthReviewedAt(LocalDateTime.now());

        userMapper.updateById(user);
    }

    // ==================== DTO / VO ====================

    /** 实名认证请求 */
    @Data
    public static class RealAuthRequest {
        /** 真实姓名 */
        private String realName;
        /** 身份证号 */
        private String idCard;
        /** 身份证正面照URL（COS存储） */
        private String idCardFrontUrl;
        /** 身份证背面照URL（COS存储） */
        private String idCardBackUrl;
        /** 手持身份证照片URL（可选，用于人工审核） */
        private String holdIdCardUrl;
    }

    /** 实名认证状态VO */
    @Data
    public static class RealAuthStatusVO {
        /** 认证状态：0未认证 1审核中 2已认证 3失败 */
        private Integer status;
        /** 状态描述 */
        private String statusDesc;
        /** 真实姓名 */
        private String realName;
        /** 脱敏身份证号 */
        private String idCardMasked;
    }

    // ==================== 私有工具 ====================

    private boolean isValidIdCard(String idCard) {
        if (idCard == null) return false;
        // 简单格式校验：18位，最后一位可以是X
        return idCard.matches("^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$");
    }

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
     * AES加密身份证
     * TODO: 接入密钥管理服务，密钥不可硬编码
     */
    private String encryptIdCard(String idCard) {
        if (idCard == null) return null;
        // 占位：实际应使用 AES-256-GCM 加密
        // 示例: return AesUtil.encrypt(idCard, secretKey);
        return "[ENCRYPTED]" + idCard; // 开发占位，生产必须替换
    }

    /**
     * AES解密身份证
     */
    private String decryptIdCard(String encrypted) {
        if (encrypted == null) return null;
        // 占位：实际应使用 AES-256-GCM 解密
        if (encrypted.startsWith("[ENCRYPTED]")) {
            return encrypted.substring(11);
        }
        return encrypted;
    }

    /**
     * 身份证脱敏：保留前6位和后4位
     * 例：110101 ******** 0011
     */
    private String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() != 18) return idCard;
        return idCard.substring(0, 6) + "********" + idCard.substring(14);
    }
}
