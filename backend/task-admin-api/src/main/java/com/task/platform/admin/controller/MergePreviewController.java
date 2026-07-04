package com.task.platform.admin.controller;

import com.task.platform.admin.dto.publish.MergePreviewReq;
import com.task.platform.admin.dto.publish.MergeResultVO;
import com.task.platform.admin.entity.PublishMergeHistory;
import com.task.platform.admin.mapper.PublishMergeHistoryMapper;
import com.task.platform.admin.service.MergePreviewService;
import com.task.platform.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 视频合并预览接口
 */
@RestController
@RequestMapping("/mobile/publish/merge-preview")
@RequiredArgsConstructor
public class MergePreviewController {

    private final MergePreviewService mergePreviewService;
    private final PublishMergeHistoryMapper publishMergeHistoryMapper;

    @PostMapping
    public ApiResponse<MergeResultVO> merge(@RequestBody MergePreviewReq req) {
        try {
            MergeResultVO result = mergePreviewService.mergePreview(req);
            return ApiResponse.success(result, "合并完成");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "合并失败: " + e.getMessage());
        }
    }

    /** 查询合并历史 */
    @GetMapping("/history")
    public ApiResponse<List<PublishMergeHistory>> getHistory(@RequestParam Long projectId) {
        List<PublishMergeHistory> list = publishMergeHistoryMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PublishMergeHistory>()
                .eq(PublishMergeHistory::getProjectId, projectId)
                .orderByDesc(PublishMergeHistory::getCreatedAt)
        );
        return ApiResponse.success(list);
    }
}
