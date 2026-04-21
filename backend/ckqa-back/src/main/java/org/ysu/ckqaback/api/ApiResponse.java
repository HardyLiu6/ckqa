package org.ysu.ckqaback.api;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 统一接口响应体。
 *
 * @param <T> 响应数据类型
 */
@Getter
public class ApiResponse<T> {

    /**
     * 业务响应码。
     */
    private final int code;

    /**
     * 响应消息。
     */
    private final String message;

    /**
     * 响应数据。
     */
    private final T data;

    /**
     * 服务端响应时间。
     */
    private final LocalDateTime timestamp;

    ApiResponse(int code, String message, T data, LocalDateTime timestamp) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = timestamp;
    }
}
