package com.task.platform.admin.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * 静态资源访问配置
 * 用于访问上传的文件
 *
 * @author TaskPlatform
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.upload.path:/uploads}")
    private String uploadPath;

    @Value("${file.upload.url-prefix:http://localhost:8084/uploads}")
    private String urlPrefix;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将 /uploads/** 映射到文件上传目录
        // urlPrefix 的格式是 http://localhost:8084/uploads，需要提取路径部分
        String resourcePath = "file:" + uploadPath + File.separator;
        
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(resourcePath);
        
        System.out.println("静态资源映射: /uploads/** -> " + resourcePath);
    }
}
