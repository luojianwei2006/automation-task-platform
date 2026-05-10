package com.task.platform.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商户表实体
 * 对应数据库表：t_merchant
 */
@Data
@TableName("t_merchant")
public class Merchant {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商户名称（企业名/个体工商户名） */
    private String name;

    /** 联系人姓名 */
    @TableField("contact_name")
    private String contactName;

    /** 手机号 */
    private String phone;

    /** 密码（BCrypt加密） */
    private String password;

    /** 营业执照号 */
    @TableField("license_no")
    private String licenseNo;

    /** 营业执照图片URL */
    @TableField("license_img")
    private String licenseImg;

    /** 法人姓名 */
    @TableField("legal_person")
    private String legalPerson;

    /** 法人身份证号（AES加密存储） */
    @TableField("legal_id_card")
    private String legalIdCard;

    /** 认证状态：0待审核 1通过 2拒绝 */
    @TableField("auth_status")
    private Integer authStatus = 0;

    /** 拒绝原因 */
    @TableField("reject_reason")
    private String rejectReason;

    /** 点数余额 */
    @TableField("point_balance")
    private BigDecimal pointBalance = BigDecimal.ZERO;

    /** 累计充值金额 */
    @TableField("total_recharge")
    private BigDecimal totalRecharge = BigDecimal.ZERO;

    /** 累计消费金额 */
    @TableField("total_consume")
    private BigDecimal totalConsume = BigDecimal.ZERO;

    /** 状态：0封禁 1正常 */
    private Integer status = 1;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
