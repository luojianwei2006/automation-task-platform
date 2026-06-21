package com.task.platform.admin.dto.publish;

import lombok.Data;

/**
 * 创建项目请求
 */
@Data
public class CreateProjectReq {

    /** 项目名称（必填） */
    private String name;

    /** 项目描述 */
    private String description;

    /** 封面图URL */
    private String coverUrl;
}
