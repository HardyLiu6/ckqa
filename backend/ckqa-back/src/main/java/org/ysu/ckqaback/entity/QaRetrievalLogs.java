package org.ysu.ckqaback.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 问答检索日志表
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Getter
@Setter
@ToString
@TableName("qa_retrieval_logs")
public class QaRetrievalLogs implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 会话ID
     */
    @TableField("session_id")
    private Long sessionId;

    /**
     * 用户消息ID
     */
    @TableField("user_message_id")
    private Long userMessageId;

    /**
     * 助手消息ID
     */
    @TableField("assistant_message_id")
    private Long assistantMessageId;

    /**
     * 任务序号
     */
    @TableField("task_seq")
    private Integer taskSeq;

    /**
     * 任务状态
     */
    @TableField("task_status")
    private String taskStatus;

    /**
     * 编排阶段
     */
    @TableField("progress_stage")
    private String progressStage;

    /**
     * Python 侧任务ID
     */
    @TableField("python_task_id")
    private String pythonTaskId;

    /**
     * 最近日志 tail
     */
    @TableField("latest_logs")
    private String latestLogs;

    /**
     * 课程ID
     */
    @TableField("course_id")
    private String courseId;

    /**
     * 索引运行ID
     */
    @TableField("index_run_id")
    private Long indexRunId;

    /**
     * 查询模式
     */
    @TableField("query_mode")
    private String queryMode;

    /**
     * 查询文本
     */
    @TableField("query_text")
    private String queryText;

    /**
     * 学生原始问题
     */
    @TableField("original_query_text")
    private String originalQueryText;

    /**
     * 实际发给 GraphRAG 的短检索问题
     */
    @TableField("retrieval_query_text")
    private String retrievalQueryText;

    /**
     * 独立检索问题
     */
    @TableField("standalone_query_text")
    private String standaloneQueryText;

    /**
     * 本轮上下文快照
     */
    @TableField("context_snapshot_text")
    private String contextSnapshotText;

    /**
     * 上下文策略
     */
    @TableField("context_strategy")
    private String contextStrategy;

    /**
     * 上下文消息范围
     */
    @TableField("context_message_range")
    private String contextMessageRange;

    /**
     * 上下文字符数估算
     */
    @TableField("context_char_count")
    private Integer contextCharCount;

    /**
     * 是否应用追问改写
     */
    @TableField("rewrite_applied")
    private Boolean rewriteApplied;

    /**
     * 追问改写原因
     */
    @TableField("rewrite_reason")
    private String rewriteReason;

    /**
     * 追问改写来源消息范围
     */
    @TableField("rewrite_source_message_range")
    private String rewriteSourceMessageRange;

    /**
     * 改写方法：none/rule/llm
     */
    @TableField("rewrite_method")
    private String rewriteMethod;

    /**
     * 改写模型
     */
    @TableField("rewrite_model")
    private String rewriteModel;

    /**
     * 改写置信度
     */
    @TableField("rewrite_confidence")
    private Double rewriteConfidence;

    /**
     * 上下文快照版本
     */
    @TableField("context_snapshot_version")
    private String contextSnapshotVersion;

    /**
     * 智能推荐置信度
     */
    @TableField("routing_confidence")
    private Double routingConfidence;

    /**
     * 智能推荐置信度分档
     */
    @TableField("routing_confidence_band")
    private String routingConfidenceBand;

    /**
     * 智能推荐复核优先级
     */
    @TableField("routing_review_priority")
    private String routingReviewPriority;

    /**
     * 学生端智能推荐诊断快照 JSON
     */
    @TableField("routing_snapshot_json")
    private String routingSnapshotJson;

    /**
     * 是否应用长期记忆
     */
    @TableField("memory_applied")
    private Boolean memoryApplied;

    /**
     * 长期记忆策略
     */
    @TableField("memory_strategy")
    private String memoryStrategy;

    /**
     * 长期记忆隔离范围
     */
    @TableField("memory_scope")
    private String memoryScope;

    /**
     * 长期记忆来源数量
     */
    @TableField("memory_source_count")
    private Integer memorySourceCount;

    /**
     * 长期记忆上下文字符数
     */
    @TableField("memory_size_chars")
    private Integer memorySizeChars;

    /**
     * Python 查询引擎策略
     */
    @TableField("query_engine_strategy")
    private String queryEngineStrategy;

    /**
     * 历史上下文降级原因
     */
    @TableField("history_fallback_reason")
    private String historyFallbackReason;

    /**
     * 内部传递给 Python 的历史上下文 JSON，不对外展示。
     */
    @TableField("memory_history_json")
    private String memoryHistoryJson;

    /**
     * 检索状态
     */
    @TableField("retrieval_status")
    private String retrievalStatus;

    /**
     * 错误信息
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 开始时间
     */
    @TableField("started_at")
    private LocalDateTime startedAt;

    /**
     * 最近心跳时间
     */
    @TableField("last_heartbeat_at")
    private LocalDateTime lastHeartbeatAt;

    /**
     * 完成时间
     */
    @TableField("finished_at")
    private LocalDateTime finishedAt;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
