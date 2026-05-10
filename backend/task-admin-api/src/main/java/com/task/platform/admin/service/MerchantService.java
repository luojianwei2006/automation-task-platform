package com.task.platform.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.task.platform.admin.entity.Merchant;
import com.task.platform.admin.mapper.MerchantMapper;
import com.task.platform.common.exception.BusinessException;
import com.task.platform.common.response.ErrorCode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商户管理服务
 *
 * @author TaskPlatform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantMapper merchantMapper;
    private final PasswordEncoder passwordEncoder;

    // ==================== 查询 ====================

    /**
     * 分页查询商户列表
     *
     * @param page     页码
     * @param size     每页数量
     * @param keyword  搜索关键词（商户名称/手机号）
     * @return 分页结果
     */
    public Page<Merchant> listMerchants(Integer page, Integer size, String keyword) {
        if (page == null || page < 1) page = 1;
        if (size == null || size < 1) size = 20;

        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<>();
        
        // 关键词搜索（商户名称或手机号）
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                    .like(Merchant::getName, keyword)
                    .or()
                    .like(Merchant::getPhone, keyword)
            );
        }
        
        wrapper.orderByDesc(Merchant::getCreatedAt);
        
        return merchantMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /**
     * 查询商户详情
     *
     * @param merchantId 商户ID
     * @return 商户信息
     */
    public Merchant getMerchantDetail(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "商户不存在");
        }
        return merchant;
    }

    /**
     * 获取所有商户列表（用于下拉选择）
     */
    public List<Merchant> getAllMerchants() {
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Merchant::getStatus, 1)
               .orderByAsc(Merchant::getName);
        return merchantMapper.selectList(wrapper);
    }

    // ==================== 新增/编辑 ====================

    /**
     * 创建商户
     *
     * @param req 创建请求
     * @return 商户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createMerchant(CreateMerchantRequest req) {
        // 检查手机号是否已存在
        if (merchantMapper.selectByPhone(req.getPhone()) != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "手机号已注册");
        }

        Merchant merchant = new Merchant();
        merchant.setName(req.getName());
        merchant.setContactName(req.getContactName());
        merchant.setPhone(req.getPhone());
        merchant.setPassword(passwordEncoder.encode(req.getPassword()));
        merchant.setLicenseNo(req.getLicenseNo());
        merchant.setLicenseImg(req.getLicenseImg());
        merchant.setLegalPerson(req.getLegalPerson());
        merchant.setLegalIdCard(req.getLegalIdCard());
        merchant.setAuthStatus(req.getAuthStatus() != null ? req.getAuthStatus() : 0);
        merchant.setPointBalance(req.getPointBalance() != null ? req.getPointBalance() : java.math.BigDecimal.ZERO);
        merchant.setStatus(req.getStatus() != null ? req.getStatus() : 1);
        merchant.setCreatedAt(LocalDateTime.now());
        merchant.setUpdatedAt(LocalDateTime.now());

        merchantMapper.insert(merchant);
        log.info("[Merchant] 创建商户成功: {}, ID: {}", merchant.getName(), merchant.getId());
        return merchant.getId();
    }

    /**
     * 更新商户信息
     *
     * @param merchantId 商户ID
     * @param req        更新请求
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateMerchant(Long merchantId, UpdateMerchantRequest req) {
        Merchant merchant = getMerchantDetail(merchantId);

        if (req.getName() != null) {
            merchant.setName(req.getName());
        }
        if (req.getContactName() != null) {
            merchant.setContactName(req.getContactName());
        }
        if (req.getPhone() != null && !req.getPhone().equals(merchant.getPhone())) {
            // 检查新手机号是否已被其他商户使用
            Merchant existMerchant = merchantMapper.selectByPhone(req.getPhone());
            if (existMerchant != null && !existMerchant.getId().equals(merchantId)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "手机号已被其他商户使用");
            }
            merchant.setPhone(req.getPhone());
        }
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            merchant.setPassword(passwordEncoder.encode(req.getPassword()));
        }
        if (req.getLicenseNo() != null) {
            merchant.setLicenseNo(req.getLicenseNo());
        }
        if (req.getLicenseImg() != null) {
            merchant.setLicenseImg(req.getLicenseImg());
        }
        if (req.getLegalPerson() != null) {
            merchant.setLegalPerson(req.getLegalPerson());
        }
        if (req.getLegalIdCard() != null) {
            merchant.setLegalIdCard(req.getLegalIdCard());
        }
        if (req.getAuthStatus() != null) {
            merchant.setAuthStatus(req.getAuthStatus());
        }
        if (req.getRejectReason() != null) {
            merchant.setRejectReason(req.getRejectReason());
        }
        if (req.getPointBalance() != null) {
            merchant.setPointBalance(req.getPointBalance());
        }
        merchant.setUpdatedAt(LocalDateTime.now());

        merchantMapper.updateById(merchant);
        log.info("[Merchant] 更新商户成功: ID={}", merchantId);
    }

    // ==================== 状态操作 ====================

    /**
     * 启用/禁用商户
     *
     * @param merchantId 商户ID
     * @param enable     true=启用, false=禁用
     */
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long merchantId, boolean enable) {
        Merchant merchant = getMerchantDetail(merchantId);
        merchant.setStatus(enable ? 1 : 0);
        merchant.setUpdatedAt(LocalDateTime.now());
        merchantMapper.updateById(merchant);
        log.info("[Merchant] 商户状态变更: ID={}, 状态={}", merchantId, enable ? "启用" : "禁用");
    }

    /**
     * 删除商户（软删除 - 实际上设置为禁用状态）
     *
     * @param merchantId 商户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteMerchant(Long merchantId) {
        Merchant merchant = getMerchantDetail(merchantId);
        merchant.setStatus(0); // 禁用
        merchant.setUpdatedAt(LocalDateTime.now());
        merchantMapper.updateById(merchant);
        log.info("[Merchant] 删除商户（禁用）: ID={}", merchantId);
    }

    // ==================== DTO ====================

    @Data
    public static class CreateMerchantRequest {
        /** 商户名称 */
        private String name;
        
        /** 联系人姓名 */
        private String contactName;
        
        /** 手机号（登录账号） */
        private String phone;
        
        /** 密码 */
        private String password;
        
        /** 营业执照号 */
        private String licenseNo;
        
        /** 营业执照图片URL */
        private String licenseImg;
        
        /** 法人姓名 */
        private String legalPerson;
        
        /** 法人身份证号 */
        private String legalIdCard;
        
        /** 认证状态：0待审核 1通过 2拒绝 */
        private Integer authStatus;
        
        /** 点数余额 */
        private java.math.BigDecimal pointBalance;
        
        /** 状态：0封禁 1正常 */
        private Integer status;
    }

    @Data
    public static class UpdateMerchantRequest {
        /** 商户名称 */
        private String name;
        
        /** 联系人姓名 */
        private String contactName;
        
        /** 手机号（登录账号） */
        private String phone;
        
        /** 密码（不修改则传空） */
        private String password;
        
        /** 营业执照号 */
        private String licenseNo;
        
        /** 营业执照图片URL */
        private String licenseImg;
        
        /** 法人姓名 */
        private String legalPerson;
        
        /** 法人身份证号 */
        private String legalIdCard;
        
        /** 认证状态：0待审核 1通过 2拒绝 */
        private Integer authStatus;
        
        /** 拒绝原因 */
        private String rejectReason;
        
        /** 点数余额 */
        private java.math.BigDecimal pointBalance;
    }
}
