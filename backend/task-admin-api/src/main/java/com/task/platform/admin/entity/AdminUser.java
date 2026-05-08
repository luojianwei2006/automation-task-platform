package com.task.platform.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员用户表
 * 统一承载：平台超管 + 商户管理员 + 商户操作员 + 财务角色
 */
@Data
@TableName("t_admin_user")
public class AdminUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号（手机号或邮箱） */
    private String username;

    /** 密码（BCrypt加密） */
    private String password;

    /** 显示名称 */
    private String displayName;

    /**
     * 角色类型：
     * 1=超级管理员（平台运营）
     * 2=商户管理员
     * 3=商户操作员
     * 4=财务
     */
    private Integer roleType;

    /** 关联商户ID（roleType=2/3/4时有效） */
    private Long merchantId;

    /** 账号状态：0禁用 1启用 */
    private Integer status = 1;

    /** 创建人ID（创建者的管理员ID） */
    private Long createdBy;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
