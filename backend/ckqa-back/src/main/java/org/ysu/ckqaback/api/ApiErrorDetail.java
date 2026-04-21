package org.ysu.ckqaback.api;

import lombok.Getter;

/**
 * 参数错误明细。
 */
@Getter
public class ApiErrorDetail {

    /**
     * 出错字段名。
     */
    private final String field;

    /**
     * 错误描述。
     */
    private final String message;

    /**
     * 创建错误明细对象。
     *
     * @param field 出错字段名
     * @param message 错误描述
     */
    public ApiErrorDetail(String field, String message) {
        this.field = field;
        this.message = message;
    }
}
