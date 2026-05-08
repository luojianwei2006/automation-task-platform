package com.task.platform.common.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一API响应体
 * 所有API返回格式统一为此结构
 * 
 * @param <T> 数据类型
 */
@Data
public class ApiResponse<T> implements Serializable {
    
    /** 状态码：200成功，其他为错误码 */
    private int code;
    
    /** 提示信息 */
    private String msg;
    
    /** 返回数据 */
    private T data;
    
    /** 时间戳（毫秒） */
    private Long timestamp = System.currentTimeMillis();
    
    // 成功响应（无数据）
    public static <T> ApiResponse<T> success() {
        return success(null);
    }
    
    // 成功响应（带数据）
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(200);
        response.setMsg("success");
        response.setData(data);
        return response;
    }

    // 成功响应（带数据 + 自定义消息）
    public static <T> ApiResponse<T> success(T data, String msg) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(200);
        response.setMsg(msg);
        response.setData(data);
        return response;
    }
    
    // 失败响应
    public static <T> ApiResponse<T> error(int code, String msg) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(code);
        response.setMsg(msg);
        return response;
    }
    
    // 失败响应（使用ErrorCode）
    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return error(errorCode.getCode(), errorCode.getMsg());
    }
}
