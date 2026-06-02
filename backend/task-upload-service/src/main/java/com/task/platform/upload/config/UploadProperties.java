package com.task.platform.upload.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件上传配置属性
 *
 * <p>绑定 application.yml 中 file.upload 前缀的配置项。</p>
 */
@Data
@ConfigurationProperties(prefix = "file.upload")
public class UploadProperties {

    /**
     * 文件存储的本地根路径
     * 示例: /Users/luojianwei/Documents/Workbuddy/automation_project/uploads
     */
    private String path;

    /**
     * 客户端访问 URL 前缀
     * 示例: /api/upload/uploads
     */
    private String urlPrefix;
}
