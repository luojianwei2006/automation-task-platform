package com.task.platform.admin.dto.publish;

import lombok.Data;

/**
 * 更新项目请求
 */
@Data
public class UpdateProjectReq {

    /** 项目名称 */
    private String name;

    /** 项目描述 */
    private String description;

    /** 封面图URL */
    private String coverUrl;
}
