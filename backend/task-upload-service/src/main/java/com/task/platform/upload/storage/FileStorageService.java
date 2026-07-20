package com.task.platform.upload.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储服务接口
 *
 * <p>定义文件上传、访问URL获取、删除的统一契约。
 * 不同实现可对接本地磁盘、OSS、MinIO 等不同存储后端。</p>
 */
public interface FileStorageService {

    /**
     * 上传文件到存储后端
     *
     * @param file 上传的 MultipartFile
     * @param type 文件类型目录（如 "image"、"qrcode"）
     * @return 相对路径，格式: /upload/uploads/{type}/{uuid}.{ext}
     * @throws com.task.platform.common.exception.BusinessException 当文件校验不通过时
     */
    String upload(MultipartFile file, String type);

    /**
     * 根据相对路径生成客户端可访问的完整 URL
     *
     * @param relativePath 上传后返回的相对路径，格式: /upload/uploads/{type}/{filename}
     * @return 客户端可直接加载的 URL，格式: /api/upload/uploads/{type}/{filename}
     */
    String getAccessUrl(String relativePath);

    /**
     * 上传视频文件到存储后端
     *
     * @param file 上传的 MultipartFile（video/*）
     * @return 相对路径，格式: /upload/uploads/video/{uuid}.{ext}
     * @throws com.task.platform.common.exception.BusinessException 当文件校验不通过时
     */
    String uploadVideo(MultipartFile file);

    /**
     * 删除存储的文件
     *
     * @param relativePath 上传后返回的相对路径
     * @return true-删除成功，false-文件不存在或删除失败
     */
    boolean delete(String relativePath);
}
