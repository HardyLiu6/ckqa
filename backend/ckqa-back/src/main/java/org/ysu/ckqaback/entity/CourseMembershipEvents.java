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
 * 课程成员关系事件表
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Getter
@Setter
@ToString
@TableName("course_membership_events")
public class CourseMembershipEvents implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 课程成员关系ID
     */
    @TableField("course_membership_id")
    private Long courseMembershipId;

    /**
     * 事件类型
     */
    @TableField("event_type")
    private String eventType;

    /**
     * 旧状态
     */
    @TableField("old_status")
    private String oldStatus;

    /**
     * 新状态
     */
    @TableField("new_status")
    private String newStatus;

    /**
     * 操作人
     */
    @TableField("operator_user_id")
    private Long operatorUserId;

    /**
     * 变更原因
     */
    @TableField("change_reason")
    private String changeReason;

    /**
     * 事件载荷
     */
    @TableField("event_payload")
    private String eventPayload;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
