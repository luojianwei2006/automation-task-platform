package com.task.platform.admin.dto.publish;

import lombok.Data;

/**
 * 完成上报请求（移动端）
 */
@Data
public class CompleteReq {

    /** 完成人ID（必填） */
    private Long userId;

    /** 完成结果信息 */
    private String resultMessage;
}
