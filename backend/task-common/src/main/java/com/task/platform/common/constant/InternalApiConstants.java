package com.task.platform.common.constant;

/**
 * 内部服务调用约定常量
 *
 * <p>服务间（admin-api / task-service → user-service）走内网直连，
 * 请求头携带 {@link #HEADER_NAME}，由 user-service 的 {@code InternalApiFilter} 校验。</p>
 */
public final class InternalApiConstants {

    private InternalApiConstants() {
    }

    /** 内部调用鉴权请求头名称 */
    public static final String HEADER_NAME = "X-Internal-Token";

    /** 共享内部密钥（与 task-task-service / task-admin-api 的 internal.api-token 配置一致） */
    public static final String DEFAULT_TOKEN = "task-internal-2026";
}
