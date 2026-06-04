package org.ysu.ckqaback.qa.dto;

import lombok.Getter;
import org.ysu.ckqaback.qa.context.BudgetSizeEstimate;

/**
 * 上下文大小估算。tokens 仅在精确 tokenizer 成功时填写，字符数保留为兼容兜底。
 */
@Getter
public class ContextSizeEstimateResponse {

    private final Integer chars;
    private final Integer tokens;
    private final String tokenizer;
    private final String fallbackReason;

    private ContextSizeEstimateResponse(Integer chars, Integer tokens, String tokenizer, String fallbackReason) {
        this.chars = chars == null ? 0 : chars;
        this.tokens = tokens;
        this.tokenizer = tokenizer;
        this.fallbackReason = fallbackReason;
    }

    public static ContextSizeEstimateResponse of(Integer chars) {
        return new ContextSizeEstimateResponse(chars, null, null, null);
    }

    public static ContextSizeEstimateResponse of(BudgetSizeEstimate estimate) {
        if (estimate == null) {
            return of(0);
        }
        return new ContextSizeEstimateResponse(
                estimate.chars(),
                estimate.tokens(),
                estimate.tokenizer(),
                estimate.fallbackReason()
        );
    }
}
