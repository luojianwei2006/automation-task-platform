package com.task.platform.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.task.platform.admin.entity.AppUser;
import com.task.platform.admin.mapper.AppUserMapper;
import com.task.platform.common.exception.BusinessException;
import com.task.platform.common.response.ErrorCode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

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

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

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

    /**
     * 管理员新增C端用户
     *
     * @param phone    手机号（明文）
     * @param password 密码（明文，方法内BCrypt加密）
     * @param nickname 昵称（可选，null则自动生成）
     */
    @Transactional(rollbackFor = Exception.class)
    public AppUser createUser(String phone, String password, String nickname) {
        // 校验手机号是否已存在
        AppUser exist = appUserMapper.selectOne(
                new LambdaQueryWrapper<AppUser>().eq(AppUser::getPhone, phone));
        if (exist != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该手机号已注册");
        }

        AppUser user = new AppUser();
        user.setPhone(phone);
        user.setPassword(PASSWORD_ENCODER.encode(password));
        user.setNickname(
                nickname != null && !nickname.isBlank() ? nickname : "用户" + phone.substring(Math.max(0, phone.length() - 4))
        );
        user.setStatus(1);
        user.setRealAuthStatus(STATUS_UNAUTHENTICATED);
        user.setInviteCode(generateInviteCode());
        user.setAutoMode(0);
        // createdAt 和 updatedAt 由 MyBatis-Plus 自动填充

        appUserMapper.insert(user);
        log.info("[Admin] 新增用户 id={} phone={}", user.getId(), phone);
        return user;
    }

    /**
     * 管理员编辑C端用户（可重置密码）
     *
     * @param userId      用户ID
     * @param nickname    新昵称（null=不修改）
     * @param newPassword 新密码明文（null或空=不修改密码）
     * @param status      账号状态（null=不修改）
     */
    @Transactional(rollbackFor = Exception.class)
    public AppUser updateUser(Long userId, String nickname, String newPassword, Integer status) {
        AppUser user = getUserById(userId);

        if (nickname != null) {
            user.setNickname(nickname);
        }
        if (newPassword != null && !newPassword.isBlank()) {
            user.setPassword(PASSWORD_ENCODER.encode(newPassword));
            log.info("[Admin] 用户 {} 密码已重置", userId);
        }
        if (status != null) {
            user.setStatus(status);
        }
        // updatedAt 由 MyBatis-Plus 自动填充

        appUserMapper.updateById(user);
        log.info("[Admin] 更新用户 id={}", userId);
        return user;
    }

    /**
     * 生成唯一6位邀请码
     * 最多重试100次，避免无限循环
     */
    private String generateInviteCode() {
        Random random = new Random();
        String code;
        int maxRetries = 100;
        int retryCount = 0;
        
        do {
            code = String.format("%06d", random.nextInt(1000000));
            retryCount++;
            
            // 如果重试次数过多，使用时间戳保证唯一性
            if (retryCount >= maxRetries) {
                code = String.format("%06d", System.currentTimeMillis() % 1000000);
            }
        } while (appUserMapper.selectOne(
                new LambdaQueryWrapper<AppUser>().eq(AppUser::getInviteCode, code)
        ) != null && retryCount < maxRetries + 10); // 额外10次重试
        
        return code;
    }

    // =================== VO ===================

    @Data
    public static class RealAuthStatusVO {
        private Integer status;
        private String statusDesc;
        private String realName;
        private String idCardMasked;
    }

    // =================== 私有工具 ===================

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
