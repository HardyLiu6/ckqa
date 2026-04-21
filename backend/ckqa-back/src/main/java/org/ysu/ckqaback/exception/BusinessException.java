package org.ysu.ckqaback.exception;

import org.springframework.http.HttpStatus;
import org.ysu.ckqaback.api.ApiResultCode;

/**
 * 统一业务异常。
 * <p>
 * 该异常用于在服务层显式抛出可预期的业务错误，
 * 由全局异常处理器统一转换为标准响应体。
 * </p>
 */
public class BusinessException extends RuntimeException {

    private final int code;
    private final HttpStatus status;

    /**
     * 创建业务异常。
     *
     * @param code 业务响应码
     * @param status HTTP 状态码
     * @param message 异常消息
     */
    public BusinessException(int code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    /**
     * 基于结果码创建业务异常，默认使用结果码中的消息。
     *
     * @param resultCode 业务结果码
     * @param status HTTP 状态码
     */
    public BusinessException(ApiResultCode resultCode, HttpStatus status) {
        this(resultCode.getCode(), status, resultCode.getMessage());
    }

    /**
     * 基于结果码创建业务异常，并允许覆盖默认消息。
     *
     * @param resultCode 业务结果码
     * @param status HTTP 状态码
     * @param message 自定义异常消息
     */
    public BusinessException(ApiResultCode resultCode, HttpStatus status, String message) {
        this(resultCode.getCode(), status, message);
    }

    public int getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
