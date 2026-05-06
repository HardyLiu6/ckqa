package org.ysu.ckqaback.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 平台用户表
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Getter
@Setter
@ToString
@TableName("users")
public class Users implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 稳定业务ID
     */
    @TableField("user_code")
    private String userCode;

    /**
     * 登录用户名
     */
    @TableField("username")
    private String username;

    /**
     * 展示名称
     */
    @TableField("display_name")
    private String displayName;

    /**
     * 密码哈希
     */
    @TableField("password_hash")
    private String passwordHash;

    /**
     * 用户状态
     */
    @TableField("status")
    private String status;

    /**
     * 最后登录时间
     */
    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * 头像存储桶
     */
    @TableField("avatar_bucket")
    private String avatarBucket;

    /**
     * 头像对象键
     */
    @TableField("avatar_object_key")
    private String avatarObjectKey;

    /**
     * 头像内容类型
     */
    @TableField("avatar_content_type")
    private String avatarContentType;

    /**
     * 头像更新时间
     */
    @TableField("avatar_updated_at")
    private LocalDateTime avatarUpdatedAt;

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

    /**
     * 逻辑删除
     */
    @TableLogic
    @TableField("is_deleted")
    private Boolean isDeleted;
}
