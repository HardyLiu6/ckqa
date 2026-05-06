package org.ysu.ckqaback.user.dto;

import lombok.Getter;
import org.ysu.ckqaback.entity.Users;
import org.ysu.ckqaback.user.UserAvatarService;

import java.time.LocalDateTime;

/**
 * 用户响应体。
 * <p>
 * 该对象面向接口输出，显式排除敏感字段，例如密码哈希。
 * </p>
 */
@Getter
public class UserResponse {

    /**
     * 用户主键。
     */
    private final Long id;

    /**
     * 用户编码。
     */
    private final String userCode;

    /**
     * 用户名。
     */
    private final String username;

    /**
     * 展示名称。
     */
    private final String displayName;

    /**
     * 头像访问地址。
     */
    private final String avatarUrl;

    /**
     * 用户状态。
     */
    private final String status;

    /**
     * 最近登录时间。
     */
    private final LocalDateTime lastLoginAt;

    /**
     * 创建时间。
     */
    private final LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    private final LocalDateTime updatedAt;

    private UserResponse(
            Long id,
            String userCode,
            String username,
            String displayName,
            String avatarUrl,
            String status,
            LocalDateTime lastLoginAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.userCode = userCode;
        this.username = username;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.status = status;
        this.lastLoginAt = lastLoginAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 从实体对象转换为接口响应对象。
     *
     * @param user 用户实体
     * @return 用户响应体
     */
    public static UserResponse fromEntity(Users user) {
        return new UserResponse(
                user.getId(),
                user.getUserCode(),
                user.getUsername(),
                user.getDisplayName(),
                UserAvatarService.resolveResponseAvatarUrl(user),
                user.getStatus(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
