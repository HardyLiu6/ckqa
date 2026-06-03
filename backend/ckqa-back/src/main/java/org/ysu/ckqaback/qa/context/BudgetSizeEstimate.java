package org.ysu.ckqaback.qa.context;

/**
 * 上下文预算估算结果：chars 始终保留，tokens 只有精确 tokenizer 成功时才填写。
 */
public record BudgetSizeEstimate(
        int chars,
        Integer tokens,
        String tokenizer,
        String fallbackReason
) {
    public BudgetSizeEstimate {
        chars = Math.max(0, chars);
    }
}
