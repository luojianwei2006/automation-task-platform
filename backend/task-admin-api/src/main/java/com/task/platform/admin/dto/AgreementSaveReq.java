package com.task.platform.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 协议保存请求体
 * 对应前端 {@code POST /admin/agreements} 的 JSON：{ type, title, contentHtml }
 *
 * @author TaskPlatform
 */
@Data
public class AgreementSaveReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 协议类型：about / privacy / register */
    @NotBlank(message = "协议类型不能为空")
    private String type;

    /** 协议标题 */
    @NotBlank(message = "协议标题不能为空")
    private String title;

    /** 协议内容 HTML 片段（标准 HTML） */
    @NotBlank(message = "协议内容不能为空")
    private String contentHtml;
}
