package org.ysu.ckqaback.qa.memory;

/**
 * 长期记忆动态注入决策。
 */
public record QaMemoryInjectionDecision(
        String longMemoryMode,
        String reason,
        boolean allowLearningTopic,
        boolean allowPreference,
        boolean allowUnresolvedFocus
) {
}
