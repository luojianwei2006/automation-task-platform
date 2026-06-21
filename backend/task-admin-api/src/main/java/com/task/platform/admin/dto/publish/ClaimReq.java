package com.task.platform.admin.dto.publish;

import lombok.Data;

/**
 * 领取任务请求（移动端）
 */
@Data
public class ClaimReq {

    /** 领取人ID（必填） */
    private Long userId;
}
