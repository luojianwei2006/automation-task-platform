package com.task.platform.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.task.platform.common.exception.BusinessException;
import com.task.platform.common.response.ErrorCode;
import com.task.platform.user.controller.UserController;
import com.task.platform.user.entity.InviteRelation;
import com.task.platform.user.entity.User;
import com.task.platform.user.mapper.InviteRelationMapper;
import com.task.platform.user.mapper.UserMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户信息服务 - 查询/更新个人资料、绑定钱包、邀请体系
 *
 * @author TaskPlatform
 */
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserMapper userMapper;
    private final InviteRelationMapper inviteRelationMapper;

    // 邀请链接前缀（实际部署时替换为真实域名）
    private static final String INVITE_BASE_URL = "https://app.taskplatform.com/invite/";

    // ==================== 用户资料 ====================

    /**
     * 获取用户个人信息
     */
    public UserProfileVO getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return buildProfileVO(user);
    }

    /**
     * 更新用户个人信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long userId, UserController.UpdateProfileRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        boolean updated = false;

        if (req.getNickname() != null && !req.getNickname().isBlank()) {
            user.setNickname(req.getNickname().trim());
            updated = true;
        }

        if (req.getAvatarUrl() != null && !req.getAvatarUrl().isBlank()) {
            user.setAvatarUrl(req.getAvatarUrl().trim());
            updated = true;
        }

        if (updated) {
            userMapper.updateById(user);
        }
    }

    /**
     * 绑定/更新收款账户（微信 or 支付宝）
     */
    @Transactional(rollbackFor = Exception.class)
    public void bindWallet(Long userId, UserController.BindWalletRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (req.getType() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "账户类型不能为空");
        }

        switch (req.getType()) {
            case 1 -> user.setWechatAccount(req.getAccount());
            case 2 -> user.setAlipayAccount(req.getAccount());
            default -> throw new BusinessException(ErrorCode.PARAM_ERROR, "账户类型不正确，1=微信 2=支付宝");
        }

        userMapper.updateById(user);
    }

    // ==================== 邀请体系 ====================

    /**
     * 获取邀请链接信息
     */
    public InviteLinkVO getInviteLink(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        InviteLinkVO vo = new InviteLinkVO();
        vo.setInviteCode(user.getInviteCode());
        vo.setInviteUrl(INVITE_BASE_URL + user.getInviteCode());
        // TODO: 生成带二维码的邀请海报URL（调用腾讯云图片合成服务）
        vo.setPosterUrl(null);
        return vo;
    }

    /**
     * 获取我的邀请记录
     */
    public InviteRecordPageVO getInviteRecords(Long userId, int page, int size) {
        // 查询邀请关系
        IPage<InviteRelation> pageResult = inviteRelationMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<InviteRelation>()
                        .eq(InviteRelation::getInviterId, userId)
                        .orderByDesc(InviteRelation::getCreatedAt)
        );

        // 组装返回数据
        List<InviteItemVO> records = pageResult.getRecords().stream().map(rel -> {
            User invitee = userMapper.selectById(rel.getInviteeId());
            InviteItemVO item = new InviteItemVO();
            item.setInviteeId(rel.getInviteeId());
            item.setInviteePhone(invitee != null ? maskPhone(invitee.getPhone()) : "已注销");
            item.setInviteeNickname(invitee != null ? invitee.getNickname() : "未知用户");
            item.setCommissionAmount(rel.getCommissionAmount());
            item.setStatus(rel.getStatus());
            item.setCreatedAt(rel.getCreatedAt());
            return item;
        }).collect(Collectors.toList());

        // 累计返佣统计
        BigDecimal totalCommission = inviteRelationMapper.sumCommissionByInviterId(userId);

        InviteRecordPageVO vo = new InviteRecordPageVO();
        vo.setTotal(pageResult.getTotal());
        vo.setPage(page);
        vo.setSize(size);
        vo.setRecords(records);
        vo.setTotalCommission(totalCommission != null ? totalCommission : BigDecimal.ZERO);
        return vo;
    }

    // ==================== VO（响应视图对象） ====================

    /** 用户资料VO */
    @Data
    public static class UserProfileVO {
        private Long id;
        private String phone;           // 脱敏手机号
        private String nickname;
        private String avatarUrl;
        private Integer realAuthStatus; // 0未认证 1审核中 2已认证 3失败
        private String inviteCode;
        private String wechatAccount;   // 脱敏
        private String alipayAccount;   // 脱敏
        private Integer autoMode;       // 自动化模式
        private LocalDateTime createdAt;
    }

    /** 邀请链接VO */
    @Data
    public static class InviteLinkVO {
        private String inviteCode;
        private String inviteUrl;
        private String posterUrl; // 邀请海报URL（含二维码）
    }

    /** 邀请记录分页VO */
    @Data
    public static class InviteRecordPageVO {
        private long total;
        private int page;
        private int size;
        private List<InviteItemVO> records;
        private BigDecimal totalCommission; // 累计返佣
    }

    /** 单条邀请记录VO */
    @Data
    public static class InviteItemVO {
        private Long inviteeId;
        private String inviteePhone;    // 脱敏
        private String inviteeNickname;
        private BigDecimal commissionAmount;
        private Integer status;         // 0进行中 1已完成
        private LocalDateTime createdAt;
    }

    // ==================== 私有工具 ====================

    private UserProfileVO buildProfileVO(User user) {
        UserProfileVO vo = new UserProfileVO();
        vo.setId(user.getId());
        vo.setPhone(maskPhone(user.getPhone()));
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setRealAuthStatus(user.getRealAuthStatus());
        vo.setInviteCode(user.getInviteCode());
        // 收款账户脱敏展示（只显示前3后2）
        vo.setWechatAccount(maskAccount(user.getWechatAccount()));
        vo.setAlipayAccount(maskAccount(user.getAlipayAccount()));
        vo.setAutoMode(user.getAutoMode());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String maskAccount(String account) {
        if (account == null || account.length() < 5) return account;
        return account.substring(0, 3) + "***" + account.substring(account.length() - 2);
    }
}
