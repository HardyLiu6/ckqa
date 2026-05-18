package org.ysu.ckqaback.auth.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录后返回给前端的用户资料。
 */
@Getter
@Builder
public class AuthUserProfile {

    private final Long id;
    private final String userCode;
    private final String username;
    private final String displayName;
    private final String avatarUrl;
    private final List<String> roles;
    private final List<String> permissions;
    private final String dataScope;

    /**
     * 联系邮箱（个人中心展示与编辑），可空。
     */
    private final String email;

    /**
     * 联系手机号（个人中心展示与编辑），可空。
     */
    private final String phone;

    /**
     * 最近一次登录时间，按 Asia/Shanghai 时区，offset-free 字符串。
     * <p>未曾登录时为 null。</p>
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private final LocalDateTime lastLoginAt;
}
