package com.task.platform.upload.controller;

import com.task.platform.common.response.ApiResponse;
import com.task.platform.upload.dto.UploadResult;
import com.task.platform.upload.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件上传控制器
 *
 * <p>Gateway StripPrefix=1 后路径直接匹配，无需 @RequestMapping 前缀。
 * 鉴权由 Gateway JwtAuthGlobalFilter 统一处理，无需 @PreAuthorize。</p>
 *
 * <p>路径映射:
 * <pre>
 * 客户端请求                    → Gateway → 转发到 upload-service
 * POST /api/upload/image        → Strip /api → POST /upload/image
 * POST /api/upload/images       → Strip /api → POST /upload/images
 * POST /api/upload/wallet-qrcode→ Strip /api → POST /upload/wallet-qrcode
 * </pre>
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class UploadController {

    /** 多文件上传最大数量 */
    private static final int MAX_FILES_COUNT = 4;

    /** 钱包二维码上传使用的固定 type */
    private static final String QRCODE_TYPE = "qrcode";

    /** 默认文件类型 */
    private static final String DEFAULT_TYPE = "image";

    private final FileStorageService storageService;

    /**
     * 单文件图片上传
     *
     * @param file 上传文件（必填），Content-Type 必须为 image/*
     * @param type 文件类型目录（可选，默认 "image"）
     * @return ApiResponse 包含上传结果
     */
    @PostMapping("/upload/image")
    public ApiResponse<UploadResult> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", required = false, defaultValue = DEFAULT_TYPE) String type) {

        // 手动校验文件非空
        if (file == null || file.isEmpty()) {
            return ApiResponse.error(400, "上传文件不能为空");
        }

        log.debug("单文件上传: type={}, originalFilename={}, size={}", type,
                file.getOriginalFilename(), file.getSize());

        UploadResult result = doUpload(file, type);
        return ApiResponse.success(result, "上传成功");
    }

    /**
     * 多文件批量上传
     *
     * @param files 上传文件列表（必填，1-4个），Content-Type 必须为 image/*
     * @return ApiResponse 包含上传结果列表
     */
    @PostMapping("/upload/images")
    public ApiResponse<List<UploadResult>> uploadImages(
            @RequestParam("files") List<MultipartFile> files) {

        // 手动校验
        if (files == null || files.isEmpty()) {
            return ApiResponse.error(400, "上传文件列表不能为空");
        }
        if (files.size() > MAX_FILES_COUNT) {
            return ApiResponse.error(400, "单次最多上传 " + MAX_FILES_COUNT + " 个文件");
        }

        log.debug("批量上传: 文件数={}", files.size());

        List<UploadResult> results = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            if (file == null || file.isEmpty()) {
                return ApiResponse.error(400, "第 " + (i + 1) + " 个文件不能为空");
            }
            results.add(doUpload(file, DEFAULT_TYPE));
        }

        return ApiResponse.success(results, "上传成功");
    }

    /**
     * 钱包二维码上传
     *
     * <p>type 固定为 "qrcode"，用于与其他图片区分存储。</p>
     *
     * @param file 上传文件（必填）
     * @return ApiResponse 包含上传结果
     */
    @PostMapping("/upload/wallet-qrcode")
    public ApiResponse<UploadResult> uploadWalletQrcode(
            @RequestParam("file") MultipartFile file) {

        // 手动校验文件非空
        if (file == null || file.isEmpty()) {
            return ApiResponse.error(400, "上传文件不能为空");
        }

        log.debug("钱包二维码上传: originalFilename={}, size={}",
                file.getOriginalFilename(), file.getSize());

        UploadResult result = doUpload(file, QRCODE_TYPE);
        return ApiResponse.success(result, "上传成功");
    }

    // ======================== 私有方法 ========================

    /**
     * 执行上传并构造结果
     *
     * @param file 上传文件
     * @param type 文件类型
     * @return UploadResult
     */
    private UploadResult doUpload(MultipartFile file, String type) {
        String relativePath = storageService.upload(file, type);
        String accessUrl = storageService.getAccessUrl(relativePath);

        return UploadResult.builder()
                .relativePath(relativePath)
                .accessUrl(accessUrl)
                .filename(file.getOriginalFilename())
                .size(file.getSize())
                .build();
    }
}
