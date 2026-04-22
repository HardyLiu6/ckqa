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
