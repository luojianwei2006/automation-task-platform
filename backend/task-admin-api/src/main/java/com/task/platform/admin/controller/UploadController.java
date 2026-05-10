package com.task.platform.admin.controller;

import com.task.platform.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 文件上传接口
 *
 * @author TaskPlatform
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/upload")
@RequiredArgsConstructor
public class UploadController {

    @Value("${file.upload.path:/uploads}")
    private String uploadPath;

    @Value("${file.upload.url-prefix:http://localhost:8080/uploads}")
    private String urlPrefix;

    /**
     * 上传图片（单张）
     * POST /api/admin/upload/image
     *
     * 权限：超管或商户管理员
     */
    @PostMapping("/image")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
    public ApiResponse<String> uploadImage(@RequestParam("file") MultipartFile file) {
        // 校验文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ApiResponse.error(400, "只能上传图片文件");
        }

        // 校验文件大小（5MB）
        if (file.getSize() > 5 * 1024 * 1024) {
            return ApiResponse.error(400, "图片大小不能超过5MB");
        }

        try {
            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null 
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : ".jpg";
            String filename = UUID.randomUUID().toString() + extension;

            // 保存文件
            File dest = new File(uploadPath, filename);
            if (!dest.getParentFile().exists()) {
                dest.getParentFile().mkdirs();
            }
            file.transferTo(dest);

            // 返回访问URL
            String fileUrl = urlPrefix + "/" + filename;
            log.info("文件上传成功: {}", fileUrl);

            return ApiResponse.success(fileUrl);
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return ApiResponse.error(500, "文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 上传多张图片（最多4张）
     * POST /api/admin/upload/images
     *
     * 权限：超管或商户管理员
     */
    @PostMapping("/images")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
    public ApiResponse<List<String>> uploadImages(@RequestParam("files") MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return ApiResponse.error(400, "请选择文件");
        }

        if (files.length > 4) {
            return ApiResponse.error(400, "最多上传4张图片");
        }

        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            // 校验文件类型
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ApiResponse.error(400, "只能上传图片文件");
            }

            // 校验文件大小（5MB）
            if (file.getSize() > 5 * 1024 * 1024) {
                return ApiResponse.error(400, "图片大小不能超过5MB");
            }

            try {
                // 生成唯一文件名
                String originalFilename = file.getOriginalFilename();
                String extension = originalFilename != null 
                        ? originalFilename.substring(originalFilename.lastIndexOf("."))
                        : ".jpg";
                String filename = UUID.randomUUID().toString() + extension;

                // 保存文件
                File dest = new File(uploadPath, filename);
                if (!dest.getParentFile().exists()) {
                    dest.getParentFile().mkdirs();
                }
                file.transferTo(dest);

                // 添加到结果列表
                String fileUrl = urlPrefix + "/" + filename;
                urls.add(fileUrl);
                log.info("文件上传成功: {}", fileUrl);
            } catch (IOException e) {
                log.error("文件上传失败", e);
                return ApiResponse.error(500, "文件上传失败: " + e.getMessage());
            }
        }

        return ApiResponse.success(urls);
    }
}
