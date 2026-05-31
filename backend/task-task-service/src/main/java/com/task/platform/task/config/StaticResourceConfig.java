package com.task.platform.task.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 静态资源配置
 * 将上传目录映射为可访问的 URL 路径
 *
 * 访问方式：
 *   Gateway: http://localhost:8080/api/task/uploads/xxx.jpg
 *   转发后: /task/uploads/xxx.jpg → 本地文件 uploadPath/xxx.jpg
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${file.upload.path:./uploads}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = uploadPath.endsWith("/") ? uploadPath : uploadPath + "/";
        // Gateway 转发后的路径是 /task/uploads/**（已去掉 /api 前缀）
        registry.addResourceHandler("/task/uploads/**")
                .addResourceLocations("file:" + location);
    }
}
