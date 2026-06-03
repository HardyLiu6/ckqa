package org.ysu.ckqaback.qa.context;

/**
 * tokenizer 不可用时的字符预算兜底；不会把字符数伪装成 token。
 */
public class CharFallbackTokenBudgetEstimator implements TokenBudgetEstimator {

    static final String TOKENIZER = "char_fallback";
    static final String FALLBACK_REASON = "tokenizer_unavailable";

    @Override
    public BudgetSizeEstimate estimate(String text, Integer charsOverride) {
        int chars = charsOverride == null ? safeText(text).length() : charsOverride;
        return new BudgetSizeEstimate(chars, null, TOKENIZER, FALLBACK_REASON);
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }
}
