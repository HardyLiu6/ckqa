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
 * 问答会话滚动摘要表。
 */
@Getter
@Setter
@ToString
@TableName("qa_session_summaries")
public class QaSessionSummaries implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("session_id")
    private Long sessionId;

    @TableField("summary_text")
    private String summaryText;

    @TableField("summary_until_sequence_no")
    private Integer summaryUntilSequenceNo;

    @TableField("source_message_count")
    private Integer sourceMessageCount;

    @TableField("status")
    private String status;

    @TableField("error_message")
    private String errorMessage;

    @TableField("model")
    private String model;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("input_char_count")
    private Integer inputCharCount;

    @TableField("output_char_count")
    private Integer outputCharCount;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
