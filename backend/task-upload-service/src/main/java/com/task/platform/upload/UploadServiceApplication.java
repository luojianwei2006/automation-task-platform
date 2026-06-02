package com.task.platform.upload;

import com.task.platform.upload.config.UploadProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 文件上传服务 - 启动类
 *
 * <p>提供图片/附件上传、静态资源访问能力。
 * 排除数据源自动配置（本服务无需数据库）。</p>
 */
@SpringBootApplication(excludeName = {
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
})
@EnableConfigurationProperties(UploadProperties.class)
public class UploadServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UploadServiceApplication.class, args);
    }
}
