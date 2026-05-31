package com.task.platform.user.controller;

import com.task.platform.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * 用户文件上传接口
 * 用于任务截图上传等场景
 */
@Slf4j
@RestController
@RequestMapping("/user/upload")
@RequiredArgsConstructor
public class UploadController {

    @Value("${file.upload.path:/uploads}")
    private String uploadPath;

    /**
     * 上传后返回的 URL 前缀
     * - 模拟器：通过 Gateway(8080) 访问，由 Gateway 路由 /api/user/uploads/** 到本服务
     * - 生产环境：改为真实域名，如 https://api.example.com/uploads
     */
    @Value("${file.upload.url-prefix:http://10.0.2.2:8080/api/user/uploads}")
    private String urlPrefix;

    /**
     * 上传截图（单张）
     * POST /api/user/upload/screenshot
     *
     * 权限：登录用户
     */
    @PostMapping("/screenshot")
    public ApiResponse<String> uploadScreenshot(@RequestParam("file") MultipartFile file) {
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
            String filename = "screenshot_" + UUID.randomUUID() + extension;

            // 保存文件
            File dest = new File(uploadPath, filename);
            if (!dest.getParentFile().exists()) {
                dest.getParentFile().mkdirs();
            }
            file.transferTo(dest);

            // 返回访问URL（确保 urlPrefix 不以 / 结尾，filename 不带 / 开头）
            String fileUrl = urlPrefix.endsWith("/")
                    ? urlPrefix + filename
                    : urlPrefix + "/" + filename;
            log.info("用户截图上传成功: {}", fileUrl);

            return ApiResponse.success(fileUrl);
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return ApiResponse.error(500, "文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 上传钱包收款码
     * POST /api/user/upload/wallet-qrcode
     */
    @PostMapping("/wallet-qrcode")
    public ApiResponse<String> uploadWalletQrcode(@RequestParam("file") MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ApiResponse.error(400, "只能上传图片文件");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            return ApiResponse.error(400, "图片大小不能超过5MB");
        }

        try {
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : ".jpg";
            String filename = "qrcode_" + UUID.randomUUID() + extension;

            File dest = new File(uploadPath, filename);
            if (!dest.getParentFile().exists()) {
                dest.getParentFile().mkdirs();
            }
            file.transferTo(dest);

            String fileUrl = urlPrefix.endsWith("/")
                    ? urlPrefix + filename
                    : urlPrefix + "/" + filename;
            log.info("收款码上传成功: {}", fileUrl);

            return ApiResponse.success(fileUrl);
        } catch (IOException e) {
            log.error("收款码上传失败", e);
            return ApiResponse.error(500, "文件上传失败: " + e.getMessage());
        }
    }
}
