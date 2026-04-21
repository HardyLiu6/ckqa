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
 * 授权判定审计表
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Getter
@Setter
@ToString
@TableName("authorization_audit_logs")
public class AuthorizationAuditLogs implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 执行判定的用户ID
     */
    @TableField("actor_user_id")
    private Long actorUserId;

    /**
     * 目标课程ID
     */
    @TableField("target_course_id")
    private String targetCourseId;

    /**
     * 目标会话ID
     */
    @TableField("target_session_id")
    private Long targetSessionId;

    /**
     * 命中的课程成员关系ID
     */
    @TableField("course_membership_id")
    private Long courseMembershipId;

    /**
     * 动作
     */
    @TableField("action")
    private String action;

    /**
     * 判定结果
     */
    @TableField("decision")
    private String decision;

    /**
     * 判定原因
     */
    @TableField("decision_reason")
    private String decisionReason;

    /**
     * 补充元数据
     */
    @TableField("extra_metadata")
    private String extraMetadata;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
