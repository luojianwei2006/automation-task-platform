package com.task.platform.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 普通用户实体（管理后台用）
 * 映射 t_user 表
 */
@Data
@TableName("t_user")
public class AppUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 手机号 */
    private String phone;

    /** 密码（BCrypt加密） */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 头像URL */
    private String avatarUrl;

    /** 真实姓名 */
    private String realName;

    /** 身份证号（AES加密） */
    private String idCard;

    /** 身份证正面照URL */
    @TableField("id_card_front_url")
    private String idCardFrontUrl;

    /** 身份证背面照URL */
    @TableField("id_card_back_url")
    private String idCardBackUrl;

    /**
     * 实名认证状态：0未认证 1审核中 2已认证 3失败
     */
    @TableField("real_auth_status")
    private Integer realAuthStatus;

    /** 手持身份证照URL */
    @TableField("hold_id_card_url")
    private String holdIdCardUrl;

    /** 实名审核备注/驳回原因 */
    @TableField("real_auth_remark")
    private String realAuthRemark;

    /** 实名审核人ID */
    @TableField("real_auth_reviewed_by")
    private Long realAuthReviewedBy;

    /** 实名审核时间 */
    @TableField("real_auth_reviewed_at")
    private LocalDateTime realAuthReviewedAt;

    /** 绑定微信账号 */
    private String wechatAccount;

    /** 绑定支付宝账号 */
    private String alipayAccount;

    /** 微信收款码URL */
    private String wechatQrcode;

    /** 支付宝收款码URL */
    private String alipayQrcode;

    /** 邀请码（唯一） */
    private String inviteCode;

    /** 邀请人ID */
    private Long inviterId;

    /** 设备指纹 */
    private String deviceFp;

    /** 自动化模式：0手动 1半自动 2深度自动 */
    @TableField("auto_mode")
    private Integer autoMode = 0;

    /** 账号状态：0封禁 1正常 */
    private Integer status = 1;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
