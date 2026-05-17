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
     * 提示词自动调优记录不存在。
     */
    PROMPT_TUNE_RUN_NOT_FOUND(4050, "提示词自动调优记录不存在"),

    /**
     * 标注样本不存在。
     */
    AUDIT_SAMPLE_NOT_FOUND(4051, "标注样本不存在"),

    /**
     * 04 步评分任务不存在（按 id 查询时）。
     * <p>由 Phase 5 引入，与 4106 EXTRACTION_EVAL_NOT_STARTED 区分：
     * 4052 是"按 id 找不到记录"，4106 是"该 buildRun 尚未启动评分"。</p>
     */
    EXTRACTION_EVAL_RUN_NOT_FOUND(4052, "评分任务不存在"),

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
     * 当前调优缓存键已有运行中的调优任务。
     */
    PROMPT_TUNE_ALREADY_RUNNING(4102, "相同选材的自动调优正在执行，请稍候"),

    /**
     * 当前 build run 存在已被人工标注的样本，强制重新生成会清空当前进度。
     */
    BUILD_RUN_HAS_ANNOTATED_SAMPLES(4103, "当前构建已有人工标注，确认覆盖请重试并设置 force=true"),

    /**
     * 课程资料已存在。
     */
    COURSE_MATERIAL_EXISTS(4100, "课程资料已存在"),

    /**
     * 课程资料展示名已存在。
     */
    COURSE_MATERIAL_DISPLAY_NAME_EXISTS(4101, "课程资料展示名已存在"),

    /**
     * PDF 解析执行失败。
     */
    PDF_PARSE_EXECUTION_FAILED(5003, "PDF解析执行失败"),

    /**
     * 索引任务执行失败。
     */
    INDEX_RUN_EXECUTION_FAILED(5004, "索引任务执行失败"),

    /**
     * 标注流水线执行失败（build_prompt_tuning_samples / build_audit_extraction_set）。
     */
    AUDIT_PIPELINE_FAILED(5005, "标注流水线执行失败"),

    /**
     * AI 预填候选生成失败（单样本 GraphRAG 抽取超时或异常）。
     */
    AI_SUGGESTION_FAILED(5006, "AI 候选生成失败"),

    /**
     * 03 步候选 prompt 生成脚本执行失败（generate_candidate_prompts.py 退出非零或超时）。
     */
    CANDIDATE_GENERATION_FAILED(5007, "候选 Prompt 生成失败"),

    /**
     * 03 步进入门控失败：02 步未完成至少 1 条样本审阅。
     */
    CANDIDATE_REQUIRES_AUDIT_COMPLETED(4104, "请先完成 02 步至少 1 条样本审阅再进入 03 步"),

    /**
     * 03 步候选未生成：build run workspace 下 manifest.json 不存在或为空。
     */
    CANDIDATES_NOT_GENERATED(4105, "本次构建尚未生成候选 Prompt，请先调用生成接口"),

    /**
     * 04 步评分尚未触发或已结束，前端依赖 status 接口判断。
     */
    EXTRACTION_EVAL_NOT_STARTED(4106, "本次构建尚未启动评分任务"),

    /**
     * 用户传入的 selectedCandidates 含未生成候选 ID（绕过前端门控直接调 API）。
     */
    INVALID_EVAL_CANDIDATE_SELECTION(4108, "选定候选 ID 不在当前构建的候选清单中"),

    /**
     * 05 步 finalize 时 04 评分尚未成功（pending / running / cancelled / failed）。
     */
    EXTRACTION_EVAL_NOT_SUCCESS(4110, "评分尚未成功，无法保存为草稿"),

    /**
     * 05 步 finalize 时传入的 candidateId 不在评分报告 candidates 中。
     */
    INVALID_FINALIZE_CANDIDATE(4111, "选定候选 ID 不在评分报告的候选清单中"),

    /**
     * 04 步评分执行失败（脚本超时、异常退出或产物缺失）。
     */
    EXTRACTION_EVAL_FAILED(5008, "评分任务执行失败"),

    /**
     * 用户选择 graphrag_tuned 但当前 build run 选材对应的自动调优产物不存在或失效。
     * 由 Phase 4.5 引入。
     */
    SEED_AUTO_TUNED_UNAVAILABLE(4109, "当前选材的自动调优产物不可用，请重新选择种子或先触发自动调优"),

    /**
     * 接口尚未实现（占位）。
     */
    PIPELINE_NOT_IMPLEMENTED(5099, "接口尚未实现");

    /**
     * 业务响应码。
     */
    private final int code;

    /**
     * 默认响应消息。
     */
    private final String message;
}
