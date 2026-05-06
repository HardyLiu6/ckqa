package org.ysu.ckqaback.auth.dto;

import lombok.Builder;
import lombok.Getter;

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
}
