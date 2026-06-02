package com.task.platform.upload.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * 静态资源配置
 *
 * <p>将本地文件路径映射为 HTTP 可访问的静态资源 URL。
 * 映射关系: /upload/uploads/** → file:{uploadPath}/
 *
 * <p>注意: Gateway StripPrefix=1 去掉 /api 后，请求路径为 /upload/uploads/...，
 * 因此此处使用 /upload/uploads/** 进行匹配。</p>
 */
@Configuration
@RequiredArgsConstructor
public class StaticResourceConfig implements WebMvcConfigurer {

    private final UploadProperties uploadProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/upload/uploads/**")
                .addResourceLocations("file:" + uploadProperties.getPath() + File.separator);
    }
}
