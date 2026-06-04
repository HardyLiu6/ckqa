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
 * 问答消息表
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Getter
@Setter
@ToString
@TableName("qa_messages")
public class QaMessages implements Serializable {

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
     * 消息角色
     */
    @TableField("role")
    private String role;

    /**
     * 消息序号
     */
    @TableField("sequence_no")
    private Integer sequenceNo;

    /**
     * 消息原始内容
     */
    @TableField("content")
    private String content;

    /**
     * 可检索文本
     */
    @TableField("content_text")
    private String contentText;

    /**
     * Token数
     */
    @TableField("token_count")
    private Integer tokenCount;

    /**
     * fork 复制来源消息ID
     */
    @TableField("copied_from_message_id")
    private Long copiedFromMessageId;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
