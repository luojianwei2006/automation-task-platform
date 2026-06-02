package com.task.platform.upload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 文件上传结果 DTO
 *
 * <p>包含上传后文件的访问信息，用于返回给客户端及持久化存储。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResult implements Serializable {

    /**
     * 相对路径，格式: /upload/uploads/{type}/{filename}
     * 用于存入数据库，后续可以通过此路径定位文件
     */
    private String relativePath;

    /**
     * 客户端可直接访问的 URL，格式: /api/upload/uploads/{type}/{filename}
     * 经过 Gateway 路由后可直接加载
     */
    private String accessUrl;

    /**
     * 原始文件名
     */
    private String filename;

    /**
     * 文件大小（字节）
     */
    private long size;
}
