package com.task.platform.common.response;

/**
 * 错误码枚举
 * 
 * 规范：
 * - 200：成功
 * - 400-499：客户端错误
 *   - 400：参数错误
 *   - 401：未授权（未登录）
 *   - 403：无权限
 *   - 404：资源不存在
 *   - 409：资源冲突（如重复提交）
 * - 500-599：服务端错误
 *   - 500：系统错误
 *   - 503：服务不可用
 */
public enum ErrorCode {
    
    // ========== 通用错误 (1000-1999) ==========
    SUCCESS(200, "success"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未授权，请先登录"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),
    CONFLICT(409, "资源冲突"),
    
    // ========== 认证相关 (2000-2999) ==========
    SMS_SEND_FAILED(2000, "短信发送失败"),
    SMS_CODE_EXPIRED(2001, "验证码已过期"),
    SMS_CODE_ERROR(2002, "验证码错误"),
    SMS_CODE_SEND_TOO_OFTEN(2003, "验证码发送过于频繁，请稍后再试"),
    PHONE_ALREADY_REGISTERED(2004, "手机号已注册"),
    PHONE_NOT_REGISTERED(2005, "手机号未注册"),
    PASSWORD_ERROR(2006, "密码错误"),
    TOKEN_INVALID(2007, "Token无效或已过期"),
    TOKEN_EXPIRED(2008, "Token已过期，请刷新"),
    
    // ========== 用户相关 (3000-3999) ==========
    USER_NOT_FOUND(3000, "用户不存在"),
    USER_DISABLED(3001, "用户已被封禁"),
    REAL_NAME_AUTH_REQUIRED(3002, "请先完成实名认证"),
    REAL_NAME_AUTH_IN_PROGRESS(3003, "实名认证审核中"),
    INVITE_CODE_INVALID(3004, "邀请码无效"),
    REAL_AUTH_ALREADY_PASSED(3005, "您已完成实名认证，无需重复提交"),
    REAL_AUTH_PENDING(3006, "实名认证正在审核中，请耐心等待"),
    
    // ========== 任务相关 (4000-4999) ==========
    TASK_NOT_FOUND(4000, "任务不存在"),
    TASK_NOT_PUBLISHED(4001, "任务未上架"),
    TASK_QUOTA_FULL(4002, "任务配额已满"),
    TASK_ALREADY_ACCEPTED(4003, "您已接取该任务"),
    TASK_SUBMIT_FAILED(4004, "任务提交失败"),
    SCREENSHOT_REQUIRED(4005, "请上传截图"),
    AI_CHECK_FAILED(4006, "AI审核失败，请重新提交"),
    
    // ========== 财务相关 (5000-5999) ==========
    INSUFFICIENT_BALANCE(5000, "余额不足"),
    WITHDRAW_AMOUNT_TOO_SMALL(5001, "提现金额过低（最低10元）"),
    WITHDRAW_AMOUNT_TOO_LARGE(5002, "提现金额过高（单笔最高5000元）"),
    WITHDRAW_NEED_MANUAL_REVIEW(5003, "提现金额超过200元，需人工审核"),
    PAYMENT_FAILED(5004, "支付失败"),
    RECHARGE_FAILED(5005, "充值失败"),
    
    // ========== 商户相关 (6000-6999) ==========
    MERCHANT_NOT_FOUND(6000, "商户不存在"),
    MERCHANT_DISABLED(6001, "商户已被封禁"),
    MERCHANT_AUTH_REQUIRED(6002, "商户未认证"),
    MERCHANT_AUTH_IN_PROGRESS(6003, "商户认证审核中"),
    INSUFFICIENT_POINTS(6004, "点数不足"),
    
    // ========== 系统错误 (9000-9999) ==========
    SYSTEM_ERROR(9000, "系统错误，请稍后重试"),
    SERVICE_UNAVAILABLE(9003, "服务暂不可用"),
    ;
    
    private final int code;
    private final String msg;
    
    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
    
    public int getCode() {
        return code;
    }
    
    public String getMsg() {
        return msg;
    }
}
