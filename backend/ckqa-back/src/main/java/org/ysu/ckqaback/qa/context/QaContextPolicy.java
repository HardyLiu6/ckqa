package org.ysu.ckqaback.qa.context;

import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 问答上下文策略注册点：集中维护模式范围、字符预算和 Python query engine 协议值。
 */
public final class QaContextPolicy {

    public static final int MAX_RECENT_MESSAGES = 6;
    public static final int MAX_RECENT_CHARS = 1800;
    public static final int MAX_SUMMARY_CHARS = 800;
    public static final int MAX_SNAPSHOT_CHARS = 3500;
    public static final int MAX_MEMORY_ITEMS = 3;
    public static final int MAX_MEMORY_CHARS = 1000;
    public static final int MAX_MEMORY_HISTORY_CHARS = 3000;
    public static final int MAX_RETRIEVAL_QUERY_CHARS = 800;
    public static final String QUERY_ENGINE_LOCAL_HISTORY = "local_history";

    private static final Set<String> RECENT_CONTEXT_MODES = Set.of("basic", "local", "hybrid_v0");
    private static final Set<String> MEMORY_CONTEXT_MODES = Set.of("local");
    private static final Pattern PRONOUN_FOLLOW_UP_PATTERN = Pattern.compile(
            ".*(它|这个|这一个|该概念|本概念|上面那个|前者|后者|这种|上述).*"
    );

    private QaContextPolicy() {
    }

    public static boolean supportsRecentContext(String mode) {
        return RECENT_CONTEXT_MODES.contains(mode);
    }

    public static boolean supportsRewrite(String mode) {
        return supportsRecentContext(mode);
    }

    public static boolean supportsMemoryContext(String mode) {
        return MEMORY_CONTEXT_MODES.contains(mode);
    }

    public static boolean isPronounFollowUp(String question) {
        return StringUtils.hasText(question) && PRONOUN_FOLLOW_UP_PATTERN.matcher(question.trim()).matches();
    }

    public static String resolveQueryEngineStrategy(String mode, String requestedStrategy, boolean hasConversationHistory) {
        String normalized = StringUtils.hasText(requestedStrategy) ? requestedStrategy.trim().toLowerCase() : "";
        boolean requestedLocalHistory = QUERY_ENGINE_LOCAL_HISTORY.equals(normalized);
        if (supportsMemoryContext(mode) && (hasConversationHistory || requestedLocalHistory)) {
            return QUERY_ENGINE_LOCAL_HISTORY;
        }
        return null;
    }
}
