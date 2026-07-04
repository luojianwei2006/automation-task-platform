package com.task.platform.admin.dto.publish;

import lombok.Data;
import java.util.List;

/**
 * 提交审核请求
 */
@Data
public class SubmitReviewReq {
    /** 截图URL列表 */
    private List<String> screenshots;
    /** 合并后的视频URL */
    private String mergedVideoUrl;
}
