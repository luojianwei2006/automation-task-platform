package com.task.platform.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户表实体类
 */
@Data
@TableName("t_user")
public class User {

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

    /**
     * 实名认证状态：0未认证 1审核中 2已认证 3失败
     */
    @TableField("real_auth_status")
    private Integer realAuthStatus;

    /** 绑定微信账号 */
    private String wechatAccount;

    /** 绑定支付宝账号 */
    private String alipayAccount;

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
