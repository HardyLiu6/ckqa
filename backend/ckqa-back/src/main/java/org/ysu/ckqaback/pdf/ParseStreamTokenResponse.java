package org.ysu.ckqaback.pdf;

import lombok.Getter;

import java.time.Instant;

/**
 * 解析进度事件流短期令牌响应。
 */
@Getter
public class ParseStreamTokenResponse {

    private final String token;
    private final Instant expiresAt;

    public ParseStreamTokenResponse(String token, Instant expiresAt) {
        this.token = token;
        this.expiresAt = expiresAt;
    }
}
