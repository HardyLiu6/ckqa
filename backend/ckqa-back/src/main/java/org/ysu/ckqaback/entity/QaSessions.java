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
 * 问答会话表
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Getter
@Setter
@ToString
@TableName("qa_sessions")
public class QaSessions implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 会话编码
     */
    @TableField("session_code")
    private String sessionCode;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 课程ID
     */
    @TableField("course_id")
    private String courseId;

    /**
     * 课程成员ID
     */
    @TableField("course_membership_id")
    private Long courseMembershipId;

    /**
     * 知识库ID
     */
    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    /**
     * 本会话固化的索引运行ID
     */
    @TableField("index_run_id")
    private Long indexRunId;

    /**
     * 索引版本固化时间
     */
    @TableField("index_locked_at")
    private LocalDateTime indexLockedAt;

    /**
     * 会话类型：formal正式问答，smoke构建冒烟验证
     */
    @TableField("session_type")
    private String sessionType;

    /**
     * 父会话ID
     */
    @TableField("parent_session_id")
    private Long parentSessionId;

    /**
     * 分支来源消息ID
     */
    @TableField("forked_from_message_id")
    private Long forkedFromMessageId;

    /**
     * 分支来源消息序号
     */
    @TableField("forked_from_sequence_no")
    private Integer forkedFromSequenceNo;

    /**
     * 分支原因
     */
    @TableField("fork_reason")
    private String forkReason;

    /**
     * transcript 契约版本
     */
    @TableField("transcript_version")
    private String transcriptVersion;

    /**
     * 会话标题
     */
    @TableField("title")
    private String title;

    /**
     * 会话状态
     */
    @TableField("status")
    private String status;

    /**
     * 是否收藏会话
     */
    @TableField("is_favorite")
    private Boolean isFavorite;

    /**
     * 最后消息时间
     */
    @TableField("last_message_at")
    private LocalDateTime lastMessageAt;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
