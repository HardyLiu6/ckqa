package org.ysu.ckqaback.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一业务响应码定义。
 * <p>
 * 该枚举用于统一维护接口返回中的业务码与默认消息，
 * 与 HTTP 状态码形成分层：HTTP 状态码表达传输层结果，
 * 业务码表达接口语义结果。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum ApiResultCode {

    /**
     * 通用成功。
     */
    SUCCESS(200, "操作成功"),

    /**
     * 通用请求错误。
     */
    BAD_REQUEST(4000, "参数错误"),

    /**
     * 参数校验失败。
     */
    VALIDATION_ERROR(4001, "参数校验失败"),

    /**
     * 通用服务端异常。
     */
    INTERNAL_ERROR(5000, "服务器内部错误"),

    /**
     * 用户不存在。
     */
    USER_NOT_FOUND(4044, "用户不存在"),

    /**
     * userCode 已存在。
     */
    USER_CODE_EXISTS(4091, "userCode已存在"),

    /**
     * username 已存在。
     */
    USERNAME_EXISTS(4092, "username已存在");

    /**
     * 业务响应码。
     */
    private final int code;

    /**
     * 默认响应消息。
     */
    private final String message;
}
