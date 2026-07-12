package com.task.platform.task.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 我的任务列表项 VO
 * 组合任务信息（Task）与用户接取记录信息（UserTaskRecord）。
 * status 保留为「任务发布状态」以兼容前端旧字段；
 * recordStatus 携带「用户记录状态」语义：0进行中 1待审核 2通过 3拒绝 4超时放弃。
 */
@Data
public class MyTaskVO {

    // ── 任务字段 ──
    private Long id;
    private String title;
    private Integer platform;
    private Integer taskType;
    private String targetUrl;
    private String requirements;
    private String requirementImages;
    private BigDecimal rewardAmount;
    private Integer totalQuota;
    private Integer usedQuota;
    private Integer dailyLimit;
    private LocalDateTime deadline;
    /** 任务发布状态：0待审核 1已上架 2已暂停 3已结束 4已拒绝 */
    private Integer status;
    private Boolean requireLocation;
    private BigDecimal locationLat;
    private BigDecimal locationLng;
    private String locationDesc;
    private String commentCategoryIds;

    // ── 用户记录字段 ──
    private Long recordId;
    /** 用户记录状态：0进行中 1待审核 2通过 3拒绝 4超时放弃 */
    private Integer recordStatus;
    private Integer submitCount;
    private String reviewResult;
    private LocalDateTime acceptedAt;
    private LocalDateTime submittedAt;
}
