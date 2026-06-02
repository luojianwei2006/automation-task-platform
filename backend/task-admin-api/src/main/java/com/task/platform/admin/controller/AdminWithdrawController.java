package com.task.platform.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.task.platform.admin.entity.WithdrawRecord;
import com.task.platform.admin.mapper.WithdrawRecordMapper;
import com.task.platform.common.response.ApiResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/admin/withdraw")
@RequiredArgsConstructor
public class AdminWithdrawController {

    private final WithdrawRecordMapper withdrawRecordMapper;

    private static final String UPLOAD_DIR = "/Users/luojianwei/Documents/Workbuddy/automation_project/uploads/";

    /** 提现列表 */
    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
    public ApiResponse<Page<WithdrawRecord>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<WithdrawRecord> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(WithdrawRecord::getStatus, status);
        wrapper.orderByDesc(WithdrawRecord::getCreatedAt);
        return ApiResponse.success(withdrawRecordMapper.selectPage(new Page<>(page, size), wrapper));
    }

    /** 审核 */
    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> review(@PathVariable Long id, @RequestBody ReviewRequest req) {
        WithdrawRecord record = withdrawRecordMapper.selectById(id);
        if (record == null) return ApiResponse.error(404, "记录不存在");
        if (record.getStatus() != 0) return ApiResponse.error(400, "当前状态不可审核");

        if (req.isPass()) {
            record.setStatus(1); // 待打款
        } else {
            record.setStatus(3); // 已拒绝
            record.setRejectReason(req.getReason());
        }
        record.setProcessedAt(LocalDateTime.now());
        withdrawRecordMapper.updateById(record);
        log.info("提现审核: id={}, pass={}", id, req.isPass());
        return ApiResponse.success(null, req.isPass() ? "审核通过" : "已拒绝");
    }

    /** 上传凭证完成打款 */
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> complete(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("transactionId") String transactionId) {
        WithdrawRecord record = withdrawRecordMapper.selectById(id);
        if (record == null) return ApiResponse.error(404, "记录不存在");
        if (record.getStatus() != 1) return ApiResponse.error(400, "当前状态不可打款");

        try {
            // 保存凭证图片
            File dir = new File(UPLOAD_DIR, "voucher");
            if (!dir.exists()) dir.mkdirs();
            String filename = "voucher_" + UUID.randomUUID() + ".jpg";
            file.transferTo(new File(dir, filename));

            record.setTransferVoucherUrl("/uploads/voucher/" + filename);
            record.setTransactionId(transactionId);
            record.setStatus(2);
            record.setProcessedAt(LocalDateTime.now());
            withdrawRecordMapper.updateById(record);

            log.info("提现打款完成: id={}, transactionId={}", id, transactionId);
            return ApiResponse.success(null, "打款完成");
        } catch (Exception e) {
            log.error("凭证上传失败", e);
            return ApiResponse.error(500, "上传失败: " + e.getMessage());
        }
    }

    @Data
    public static class ReviewRequest {
        private boolean pass;
        private String reason;
    }
}
