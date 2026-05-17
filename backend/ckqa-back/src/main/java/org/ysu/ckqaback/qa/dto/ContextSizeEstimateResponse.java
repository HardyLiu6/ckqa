package org.ysu.ckqaback.qa.dto;

import lombok.Getter;

/**
 * 上下文大小估算。Phase 1 仅使用字符数，避免伪装成精确 token。
 */
@Getter
public class ContextSizeEstimateResponse {

    private final Integer chars;

    private ContextSizeEstimateResponse(Integer chars) {
        this.chars = chars == null ? 0 : chars;
    }

    public static ContextSizeEstimateResponse of(Integer chars) {
        return new ContextSizeEstimateResponse(chars);
    }
}
