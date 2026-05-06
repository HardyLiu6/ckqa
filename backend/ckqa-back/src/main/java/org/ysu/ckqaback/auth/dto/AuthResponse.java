package org.ysu.ckqaback.auth.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 登录/注册成功响应。
 */
@Getter
@Builder
public class AuthResponse {

    private final String accessToken;
    private final String tokenType;
    private final LocalDateTime expiresAt;
    private final AuthUserProfile user;
}
