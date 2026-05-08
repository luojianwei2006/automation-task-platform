package com.task.platform.common.exception;

import com.task.platform.common.response.ErrorCode;
import lombok.Getter;

/**
 * 业务异常类
 * 抛出时直接返回对应的错误码和消息
 * 
 * 使用示例：
 * throw new BusinessException(ErrorCode.USER_NOT_FOUND);
 * throw new BusinessException(ErrorCode.PARAM_ERROR, "手机号格式不正确");
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.code = errorCode.getCode();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
