package org.ysu.ckqaback.qa.context;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * 使用 o200k_base 做精确 token 统计；失败时退回字符预算诊断。
 */
@Service
public class JtokkitTokenBudgetEstimator implements TokenBudgetEstimator {

    private static final Logger LOGGER = LoggerFactory.getLogger(JtokkitTokenBudgetEstimator.class);
    private static final String ENCODING_NAME = "o200k_base";
    private static final String TOKENIZER = "jtokkit:" + ENCODING_NAME;

    private final Encoding encoding;
    private final String initFallbackReason;

    public JtokkitTokenBudgetEstimator() {
        this(() -> Encodings.newLazyEncodingRegistry());
    }

    JtokkitTokenBudgetEstimator(Supplier<EncodingRegistry> registrySupplier) {
        Encoding resolvedEncoding = null;
        String fallbackReason = null;
        try {
            EncodingRegistry registry = registrySupplier.get();
            resolvedEncoding = registry.getEncoding(ENCODING_NAME).orElse(null);
            if (resolvedEncoding == null) {
                fallbackReason = "encoding_unavailable";
                LOGGER.warn("Jtokkit encoding {} is unavailable, token budget falls back to chars.", ENCODING_NAME);
            }
        } catch (RuntimeException | LinkageError exception) {
            fallbackReason = "tokenizer_init_failed";
            LOGGER.warn("Jtokkit tokenizer init failed, token budget falls back to chars. type={} reason={}",
                    exception.getClass().getSimpleName(),
                    exception.getMessage());
        }
        this.encoding = resolvedEncoding;
        this.initFallbackReason = fallbackReason;
    }

    @Override
    public BudgetSizeEstimate estimate(String text, Integer charsOverride) {
        String safeText = text == null ? "" : text;
        int chars = charsOverride == null ? safeText.length() : charsOverride;
        if (encoding == null) {
            return fallback(chars, initFallbackReason == null ? "tokenizer_unavailable" : initFallbackReason);
        }
        try {
            return new BudgetSizeEstimate(chars, encoding.countTokensOrdinary(safeText), TOKENIZER, null);
        } catch (RuntimeException exception) {
            LOGGER.warn("Jtokkit token counting failed, token budget falls back to chars.", exception);
            return fallback(chars, "tokenizer_count_failed");
        }
    }

    private BudgetSizeEstimate fallback(int chars, String reason) {
        return new BudgetSizeEstimate(chars, null, CharFallbackTokenBudgetEstimator.TOKENIZER, reason);
    }
}
