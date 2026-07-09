package com.task.platform.admin.dto.publish;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 发布任务视图对象
 */
@Data
public class PublishTaskVO {

    private Long id;
    private Long projectId;
    private String projectName;
    private String platforms;
    private String platform;
    private String publishText;
    private LocalDateTime scheduledAt;
    private String status;
    /** 当前用户提交记录状态：CLAIMED/MERGED/SUBMITTED/PASSED/REJECTED（列表精确态用） */
    private String submissionStatus;
    private Long claimedBy;
    private LocalDateTime claimedAt;
    private LocalDateTime completedAt;
    private LocalDateTime publishedAt;
    private String errorMessage;
    private Integer retryCount;
    private Integer maxRetry;
    private String remark;
    private BigDecimal rewardAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 任务图片URL列表（JSON数组） */
    private String images;

    /** 关联项目素材列表（任务详情时填充） */
    private List<MaterialListVO> materials;

    /** 所属商户ID（来自项目） */
    private Long merchantId;

    /** 所属商户名称 */
    private String merchantName;

    // ==================== 服务费 / 预算展示字段 ====================

    /** 总配额（可领取/完成的总次数） */
    private Integer totalQuota;

    /** 已用配额（已成功结算次数） */
    private Integer usedQuota;

    /** 预算点数（含服务费，只读展示） */
    private java.math.BigDecimal budgetPoints;

    /** 已消耗点数（已结算累计含费成本） */
    private java.math.BigDecimal usedPoints;

    /** 服务费率（来自商户 merchant.service_fee_rate，如 0.15 表示 15%） */
    private java.math.BigDecimal serviceFeeRate;
}
