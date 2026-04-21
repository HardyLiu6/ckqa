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
 * 课程成员关系表
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Getter
@Setter
@ToString
@TableName("course_memberships")
public class CourseMemberships implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

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
     * 课程内角色
     */
    @TableField("membership_role")
    private String membershipRole;

    /**
     * 成员状态
     */
    @TableField("status")
    private String status;

    /**
     * 授权来源
     */
    @TableField("access_source")
    private String accessSource;

    /**
     * 来源类型
     */
    @TableField("source_ref_type")
    private String sourceRefType;

    /**
     * 来源ID
     */
    @TableField("source_ref_id")
    private String sourceRefId;

    /**
     * 加入时间
     */
    @TableField("joined_at")
    private LocalDateTime joinedAt;

    /**
     * 过期时间
     */
    @TableField("expires_at")
    private LocalDateTime expiresAt;

    /**
     * 生效开始时间
     */
    @TableField("effective_from")
    private LocalDateTime effectiveFrom;

    /**
     * 生效结束时间
     */
    @TableField("effective_to")
    private LocalDateTime effectiveTo;

    /**
     * 授权人
     */
    @TableField("granted_by_user_id")
    private Long grantedByUserId;

    /**
     * 撤销人
     */
    @TableField("revoked_by_user_id")
    private Long revokedByUserId;

    /**
     * 变更原因
     */
    @TableField("change_reason")
    private String changeReason;

    /**
     * 扩展元数据
     */
    @TableField("extra_metadata")
    private String extraMetadata;

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
