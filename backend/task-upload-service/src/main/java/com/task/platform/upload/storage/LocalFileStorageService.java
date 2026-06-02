package com.task.platform.upload.storage;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.task.platform.common.exception.BusinessException;
import com.task.platform.upload.config.UploadProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 本地文件存储服务实现
 *
 * <p>将文件存储到服务器本地磁盘，适用于单机部署场景。
 * 仅允许上传 image/* 类型的文件，单文件最大 5MB。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalFileStorageService implements FileStorageService {

    /** 允许的 Content-Type 前缀 */
    private static final String ALLOWED_CONTENT_TYPE_PREFIX = "image/";

    /** 单文件最大大小：5MB */
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    /** 允许的图片扩展名 */
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg"
    ));

    /** 相对路径固定前缀 */
    private static final String RELATIVE_PATH_PREFIX = "/upload/uploads/";

    private final UploadProperties uploadProperties;

    @Override
    public String upload(MultipartFile file, String type) {
        // 1. 校验文件
        validateFile(file);

        // 2. 确定目录并创建
        Path dirPath = ensureDirectory(type);

        // 3. 生成唯一文件名
        String filename = generateFilename(file);

        // 4. 写入磁盘
        Path filePath = dirPath.resolve(filename);
        try {
            file.transferTo(filePath.toFile());
            log.info("文件上传成功: type={}, filename={}, path={}", type, filename, filePath);
        } catch (IOException e) {
            log.error("文件写入失败: {}", filePath, e);
            throw new BusinessException(9000, "文件保存失败，请稍后重试");
        }

        // 5. 返回相对路径
        return RELATIVE_PATH_PREFIX + type + "/" + filename;
    }

    @Override
    public String getAccessUrl(String relativePath) {
        if (StrUtil.isBlank(relativePath)) {
            throw new BusinessException(400, "相对路径不能为空");
        }

        // relativePath 格式: /upload/uploads/{type}/{filename}
        // 从 relativePath 中提取 type 和 filename
        String pathWithoutPrefix = relativePath;
        if (pathWithoutPrefix.startsWith(RELATIVE_PATH_PREFIX)) {
            pathWithoutPrefix = pathWithoutPrefix.substring(RELATIVE_PATH_PREFIX.length());
        }

        // pathWithoutPrefix 格式: {type}/{filename}
        int slashIndex = pathWithoutPrefix.indexOf('/');
        if (slashIndex <= 0) {
            throw new BusinessException(400, "相对路径格式错误: " + relativePath);
        }

        String type = pathWithoutPrefix.substring(0, slashIndex);
        String filename = pathWithoutPrefix.substring(slashIndex + 1);

        // 构建 accessUrl: /api/upload/uploads/{type}/{filename}
        return uploadProperties.getUrlPrefix() + "/" + type + "/" + filename;
    }

    @Override
    public boolean delete(String relativePath) {
        if (StrUtil.isBlank(relativePath)) {
            return false;
        }

        // 从相对路径构建绝对路径
        Path targetPath = toAbsolutePath(relativePath);
        try {
            boolean deleted = Files.deleteIfExists(targetPath);
            if (deleted) {
                log.info("文件删除成功: {}", targetPath);
            } else {
                log.warn("文件不存在或删除失败: {}", targetPath);
            }
            return deleted;
        } catch (IOException e) {
            log.error("文件删除异常: {}", targetPath, e);
            return false;
        }
    }

    // ======================== 私有方法 ========================

    /**
     * 校验上传文件
     *
     * @param file MultipartFile
     * @throws BusinessException 校验不通过时抛出
     */
    private void validateFile(MultipartFile file) {
        // 非空校验
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "上传文件不能为空");
        }

        // 大小校验
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(400, "文件大小不能超过 5MB");
        }

        // Content-Type 校验
        String contentType = file.getContentType();
        if (StrUtil.isBlank(contentType) || !contentType.startsWith(ALLOWED_CONTENT_TYPE_PREFIX)) {
            throw new BusinessException(400, "仅支持上传图片格式文件（image/*），当前类型: " + contentType);
        }

        // 扩展名校验
        String originalFilename = file.getOriginalFilename();
        if (StrUtil.isNotBlank(originalFilename)) {
            String ext = FileUtil.extName(originalFilename).toLowerCase();
            if (StrUtil.isBlank(ext) || !ALLOWED_EXTENSIONS.contains(ext)) {
                throw new BusinessException(400, "不支持的图片格式: ." + ext + "，支持: " + ALLOWED_EXTENSIONS);
            }
        }
    }

    /**
     * 生成唯一文件名
     *
     * @param file MultipartFile
     * @return UUID文件名，格式: {uuid}.{ext}
     */
    private String generateFilename(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String ext = "jpg"; // 默认扩展名
        if (StrUtil.isNotBlank(originalFilename)) {
            ext = FileUtil.extName(originalFilename).toLowerCase();
            if (StrUtil.isBlank(ext)) {
                ext = "jpg";
            }
        }
        return IdUtil.simpleUUID() + "." + ext;
    }

    /**
     * 确保目标目录存在（不存在则创建）
     *
     * @param type 文件类型（子目录名）
     * @return 目录 Path
     */
    private Path ensureDirectory(String type) {
        String basePath = uploadProperties.getPath();
        if (StrUtil.isBlank(basePath)) {
            throw new BusinessException(9000, "文件上传路径未配置");
        }

        Path dirPath = Paths.get(basePath, type);
        try {
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
                log.info("创建上传目录: {}", dirPath);
            }
        } catch (IOException e) {
            log.error("创建目录失败: {}", dirPath, e);
            throw new BusinessException(9000, "创建文件存储目录失败");
        }
        return dirPath;
    }

    /**
     * 将相对路径转换为绝对路径
     *
     * @param relativePath 相对路径，如 /upload/uploads/image/abc.jpg
     * @return 绝对文件路径
     */
    private Path toAbsolutePath(String relativePath) {
        String basePath = uploadProperties.getPath();
        // relativePath 格式: /upload/uploads/{type}/{filename}
        String relative = relativePath;
        if (relative.startsWith(RELATIVE_PATH_PREFIX)) {
            relative = relative.substring(RELATIVE_PATH_PREFIX.length());
        }
        return Paths.get(basePath, relative);
    }
}
