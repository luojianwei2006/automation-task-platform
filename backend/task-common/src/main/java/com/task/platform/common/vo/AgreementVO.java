package com.task.platform.common.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 协议文档视图对象（VO）
 * 写接口（admin-api）与读接口（user-service）统一返回此结构，字段命名与安卓端
 * {@code AgreementVO} 对齐，便于前端/安卓直接解析。
 *
 * <p>{@code updatedAt} 已格式化为 {@code yyyy-MM-dd HH:mm:ss} 字符串，避免各端对
 * LocalDateTime 的序列化差异（安卓 Gson / 前端 JSON 均可稳定解析）。</p>
 *
 * @author TaskPlatform
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgreementVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 协议类型：about / privacy / register */
    private String type;

    /** 协议标题 */
    private String title;

    /** 协议内容 HTML 片段 */
    private String contentHtml;

    /** 版本号 */
    private Integer version;

    /** 更新时间（格式化字符串） */
    private String updatedAt;
}
