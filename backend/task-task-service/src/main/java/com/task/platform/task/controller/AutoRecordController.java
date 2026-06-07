package com.task.platform.task.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.task.platform.common.response.ApiResponse;
import com.task.platform.task.entity.AutoRecord;
import com.task.platform.task.mapper.AutoRecordMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 自动化操作记录接口
 *
 * 提供自动化执行步骤的日志记录与查询功能：
 * - POST /task/auto/record  保存操作日志
 * - GET  /task/auto/records  查询某任务的自动化日志
 */
@RestController
@RequestMapping("/task/auto")
@RequiredArgsConstructor
@Slf4j
public class AutoRecordController {

    private final AutoRecordMapper autoRecordMapper;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 保存自动化操作日志
     * POST /task/auto/record
     *
     * 请求体：
     * {
     *   "userId": 1,
     *   "taskId": 100,
     *   "step": "open_app",
     *   "action": "打开抖音APP",
     *   "status": 0,
     *   "result": "正在打开..."
     * }
     */
    @PostMapping("/record")
    public ApiResponse<AutoRecord> saveRecord(@RequestBody SaveRecordRequest req) {
        AutoRecord record = new AutoRecord();
        record.setUserId(req.getUserId());
        record.setTaskId(req.getTaskId());
        record.setStep(req.getStep());
        record.setAction(req.getAction());
        record.setStatus(req.getStatus() != null ? req.getStatus() : 0);
        record.setResult(req.getResult());

        autoRecordMapper.insert(record);
        log.info("自动化操作记录已保存: userId={}, taskId={}, step={}, status={}",
                req.getUserId(), req.getTaskId(), req.getStep(), req.getStatus());

        return ApiResponse.success(record, "记录已保存");
    }

    /**
     * 查询某任务的自动化操作日志
     * GET /task/auto/records?taskId=100
     */
    @GetMapping("/records")
    public ApiResponse<List<AutoRecord>> getRecords(@RequestParam Long taskId) {
        LambdaQueryWrapper<AutoRecord> wrapper = new LambdaQueryWrapper<AutoRecord>()
                .eq(AutoRecord::getTaskId, taskId)
                .orderByAsc(AutoRecord::getCreatedAt);

        List<AutoRecord> records = autoRecordMapper.selectList(wrapper);
        return ApiResponse.success(records);
    }

    // ==================== DTO ====================

    @Data
    public static class SaveRecordRequest {
        private Long userId;
        private Long taskId;
        private String step;
        private String action;
        private Integer status;
        private String result;
    }

    /**
     * 根据分类ID列表获取评论词（供自动化执行时调用）
     * GET /task/auto/comment-words?categoryIds=1,3,5
     */
    @GetMapping("/comment-words")
    public ApiResponse<List<String>> getCommentWords(@RequestParam String categoryIds) {
        if (categoryIds == null || categoryIds.isBlank()) {
            return ApiResponse.success(List.of("支持一下")); // 默认兜底
        }
        // 构建 IN 查询
        String[] ids = categoryIds.split(",");
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.length, "?"));
        List<String> words = jdbcTemplate.queryForList(
            "SELECT content FROM t_comment_word WHERE category_id IN (" + placeholders + ")",
            (Object[]) ids,
            String.class
        );
        if (words.isEmpty()) words = List.of("支持一下");
        return ApiResponse.success(words);
    }
}
