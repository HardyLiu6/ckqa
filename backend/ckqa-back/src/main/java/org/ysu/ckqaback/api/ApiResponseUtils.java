package org.ysu.ckqaback.api;

import java.time.LocalDateTime;

/**
 * 统一响应体构造工具。
 */
public final class ApiResponseUtils {

    private ApiResponseUtils() {
    }

    /**
     * 构造无数据的成功响应。
     *
     * @return 成功响应
     * @param <T> 响应数据类型
     */
    public static <T> ApiResponse<T> success() {
        return result(ApiResultCode.SUCCESS, null);
    }

    /**
     * 构造默认成功消息的成功响应。
     *
     * @param data 响应数据
     * @return 成功响应
     * @param <T> 响应数据类型
     */
    public static <T> ApiResponse<T> success(T data) {
        return result(ApiResultCode.SUCCESS, data);
    }

    /**
     * 构造自定义消息的成功响应。
     *
     * @param message 自定义消息
     * @param data 响应数据
     * @return 成功响应
     * @param <T> 响应数据类型
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(ApiResultCode.SUCCESS.getCode(), message, data, LocalDateTime.now());
    }

    /**
     * 基于结果码构造失败响应。
     *
     * @param resultCode 结果码
     * @return 失败响应
     * @param <T> 响应数据类型
     */
    public static <T> ApiResponse<T> error(ApiResultCode resultCode) {
        return result(resultCode, null);
    }

    /**
     * 基于结果码构造响应。
     *
     * @param resultCode 结果码
     * @return 响应对象
     * @param <T> 响应数据类型
     */
    public static <T> ApiResponse<T> result(ApiResultCode resultCode) {
        return result(resultCode, null);
    }

    /**
     * 基于结果码与数据构造响应。
     *
     * @param resultCode 结果码
     * @param data 响应数据
     * @return 响应对象
     * @param <T> 响应数据类型
     */
    public static <T> ApiResponse<T> result(ApiResultCode resultCode, T data) {
        return new ApiResponse<>(resultCode.getCode(), resultCode.getMessage(), data, LocalDateTime.now());
    }

    /**
     * 基于指定业务码和消息构造失败响应。
     *
     * @param code 业务码
     * @param message 自定义消息
     * @param data 响应数据
     * @return 失败响应
     * @param <T> 响应数据类型
     */
    public static <T> ApiResponse<T> error(int code, String message, T data) {
        return new ApiResponse<>(code, message, data, LocalDateTime.now());
    }
}
