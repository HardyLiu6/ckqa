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
 * 问答消息学生反馈表。
 */
@Getter
@Setter
@ToString
@TableName("qa_message_feedback")
public class QaMessageFeedback implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("message_id")
    private Long messageId;

    @TableField("retrieval_log_id")
    private Long retrievalLogId;

    @TableField("session_id")
    private Long sessionId;

    @TableField("user_id")
    private Long userId;

    @TableField("course_id")
    private String courseId;

    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    @TableField("rating")
    private String rating;

    @TableField("tags")
    private String tags;

    @TableField("comment")
    private String comment;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
