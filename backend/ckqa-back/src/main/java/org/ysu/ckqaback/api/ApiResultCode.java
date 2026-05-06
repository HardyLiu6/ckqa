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
     * 未提供认证信息。
     */
    AUTH_REQUIRED(4010, "请先登录"),

    /**
     * 认证凭据无效。
     */
    AUTH_INVALID(4011, "登录状态无效"),

    /**
     * 已认证但无权访问。
     */
    AUTH_FORBIDDEN(4030, "无权限访问"),

    /**
     * 通用服务端异常。
     */
    INTERNAL_ERROR(5000, "服务器内部错误"),

    /**
     * 课程不存在。
     */
    COURSE_NOT_FOUND(4043, "课程不存在"),

    /**
     * 用户不存在。
     */
    USER_NOT_FOUND(4044, "用户不存在"),

    /**
     * PDF 文件不存在。
     */
    PDF_FILE_NOT_FOUND(4045, "PDF文件不存在"),

    /**
     * 知识库不存在。
     */
    KNOWLEDGE_BASE_NOT_FOUND(4046, "知识库不存在"),

    /**
     * 索引任务不存在。
     */
    INDEX_RUN_NOT_FOUND(4047, "索引任务不存在"),

    /**
     * 问答会话不存在。
     */
    QA_SESSION_NOT_FOUND(4048, "问答会话不存在"),

    /**
     * 知识库构建流水线不存在。
     */
    KNOWLEDGE_BASE_BUILD_RUN_NOT_FOUND(4049, "知识库构建流水线不存在"),

    /**
     * courseId 已存在。
     */
    COURSE_ID_EXISTS(4090, "课程ID已存在"),

    /**
     * userCode 已存在。
     */
    USER_CODE_EXISTS(4091, "userCode已存在"),

    /**
     * username 已存在。
     */
    USERNAME_EXISTS(4092, "username已存在"),

    /**
     * PDF 当前状态不允许再次触发解析。
     */
    PDF_PARSE_STATE_CONFLICT(4093, "PDF当前状态不允许再次触发解析"),

    /**
     * 当前已有导出任务在执行。
     */
    PDF_EXPORT_LOCKED(4094, "当前已有导出任务在执行"),

    /**
     * 当前知识库已有索引任务在运行。
     */
    INDEX_RUN_ALREADY_RUNNING(4095, "当前知识库已有索引任务在运行"),

    /**
     * 问答会话已关闭。
     */
    QA_SESSION_NOT_ACTIVE(4096, "问答会话已关闭"),

    /**
     * 知识库当前没有可用索引。
     */
    KNOWLEDGE_BASE_NOT_READY(4097, "知识库当前没有可用索引"),

    /**
     * 同一课程下知识库编码已存在。
     */
    KNOWLEDGE_BASE_CODE_EXISTS(4098, "知识库编码已存在"),

    /**
     * 当前知识库已有构建流水线未完成。
     */
    KNOWLEDGE_BASE_BUILD_RUN_ALREADY_RUNNING(4099, "当前知识库已有构建流水线未完成"),

    /**
     * PDF 解析执行失败。
     */
    PDF_PARSE_EXECUTION_FAILED(5003, "PDF解析执行失败"),

    /**
     * 索引任务执行失败。
     */
    INDEX_RUN_EXECUTION_FAILED(5004, "索引任务执行失败");

    /**
     * 业务响应码。
     */
    private final int code;

    /**
     * 默认响应消息。
     */
    private final String message;
}
