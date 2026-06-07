package com.task.platform.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.task.platform.admin.entity.CommentCategory;
import com.task.platform.admin.entity.CommentWord;
import com.task.platform.admin.mapper.CommentCategoryMapper;
import com.task.platform.admin.mapper.CommentWordMapper;
import com.task.platform.common.response.ApiResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentCategoryMapper categoryMapper;
    private final CommentWordMapper wordMapper;

    // ========== 分类 ==========

    @GetMapping("/categories")
    public ApiResponse<List<CommentCategory>> listCategories() {
        return ApiResponse.success(categoryMapper.selectList(
            new LambdaQueryWrapper<CommentCategory>().orderByAsc(CommentCategory::getSortOrder)));
    }

    @PostMapping("/categories")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<CommentCategory> addCategory(@RequestBody CategoryReq req) {
        CommentCategory cat = new CommentCategory();
        cat.setName(req.getName());
        cat.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        categoryMapper.insert(cat);
        return ApiResponse.success(cat, "添加成功");
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> updateCategory(@PathVariable Long id, @RequestBody CategoryReq req) {
        CommentCategory cat = categoryMapper.selectById(id);
        if (cat == null) return ApiResponse.error(404, "分类不存在");
        if (req.getName() != null) cat.setName(req.getName());
        if (req.getSortOrder() != null) cat.setSortOrder(req.getSortOrder());
        categoryMapper.updateById(cat);
        return ApiResponse.success(null, "更新成功");
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        CommentCategory cat = categoryMapper.selectById(id);
        if (cat == null) return ApiResponse.error(404, "不存在");
        if (cat.getIsDefault() != null && cat.getIsDefault() == 1)
            return ApiResponse.error(400, "默认分类不可删除");
        // 删除分类下的词
        wordMapper.delete(new LambdaQueryWrapper<CommentWord>().eq(CommentWord::getCategoryId, id));
        categoryMapper.deleteById(id);
        return ApiResponse.success(null, "已删除");
    }

    // ========== 评论词 ==========

    @GetMapping("/words")
    public ApiResponse<List<CommentWord>> listWords(@RequestParam(required = false) Long categoryId) {
        LambdaQueryWrapper<CommentWord> w = new LambdaQueryWrapper<>();
        if (categoryId != null) w.eq(CommentWord::getCategoryId, categoryId);
        w.orderByDesc(CommentWord::getCreatedAt);
        return ApiResponse.success(wordMapper.selectList(w));
    }

    @PostMapping("/words")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<CommentWord> addWord(@RequestBody WordReq req) {
        CommentWord w = new CommentWord();
        w.setCategoryId(req.getCategoryId());
        w.setContent(req.getContent());
        wordMapper.insert(w);
        return ApiResponse.success(w, "添加成功");
    }

    @DeleteMapping("/words/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> deleteWord(@PathVariable Long id) {
        wordMapper.deleteById(id);
        return ApiResponse.success(null, "已删除");
    }

    @Data
    public static class CategoryReq { private String name; private Integer sortOrder; }
    @Data
    public static class WordReq { private Long categoryId; private String content; }
}
