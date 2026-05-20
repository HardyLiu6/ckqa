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
 * 用户学习长期记忆条目。
 */
@Getter
@Setter
@ToString
@TableName("qa_learning_memories")
public class QaLearningMemories implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("course_id")
    private String courseId;

    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    @TableField("index_run_id")
    private Long indexRunId;

    @TableField("memory_type")
    private String memoryType;

    @TableField("memory_text")
    private String memoryText;

    @TableField("source_session_id")
    private Long sourceSessionId;

    @TableField("source_message_id")
    private Long sourceMessageId;

    @TableField("status")
    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
