package org.ysu.ckqaback.qa.memory;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.entity.QaLearningMemories;

import java.util.regex.Pattern;

/**
 * 低成本规则式长期记忆注入路由。
 */
@Component
public class QaMemoryInjectionRouter {

    private static final Pattern PRONOUN_PATTERN = Pattern.compile(".*(它|这个|这一个|该概念|上面那个|前者|后者|这种|上述).*");
    private static final Pattern DEFINITION_PATTERN = Pattern.compile("^(什么是|请解释|解释一下|介绍一下).+");

    public QaMemoryInjectionDecision decide(String question, String latestTopic) {
        String safeQuestion = normalize(question);
        if (hasText(latestTopic) && PRONOUN_PATTERN.matcher(safeQuestion).matches()) {
            return new QaMemoryInjectionDecision(
                    "preference_only",
                    "pronoun_followup_uses_current_session_topic",
                    false,
                    true,
                    false
            );
        }
        if (isExplicitContinuation(safeQuestion)) {
            return new QaMemoryInjectionDecision(
                    "relevant_memory",
                    "explicit_learning_continuation",
                    true,
                    true,
                    true
            );
        }
        if (DEFINITION_PATTERN.matcher(safeQuestion).matches()) {
            return new QaMemoryInjectionDecision(
                    "preference_only",
                    "independent_definition_question",
                    false,
                    true,
                    false
            );
        }
        return new QaMemoryInjectionDecision(
                "preference_only",
                "default_preference_only",
                false,
                true,
                false
        );
    }

    public boolean shouldInclude(
            QaMemoryInjectionDecision decision,
            QaLearningMemories memory,
            String question,
            String latestTopic
    ) {
        if (decision == null || memory == null || !StringUtils.hasText(memory.getMemoryText())) {
            return false;
        }
        return switch (String.valueOf(memory.getMemoryType())) {
            case "explanation_preference" -> decision.allowPreference();
            case "unresolved_focus" -> decision.allowUnresolvedFocus();
            case "learning_topic" -> decision.allowLearningTopic()
                    && topicMatches(memory.getMemoryText(), question, latestTopic);
            default -> false;
        };
    }

    private boolean topicMatches(String memoryText, String question, String latestTopic) {
        if (!hasText(latestTopic) && isExplicitContinuation(normalize(question))) {
            return true;
        }
        String anchor = normalize(firstText(latestTopic, extractDefinitionTopic(question)));
        if (!StringUtils.hasText(anchor)) {
            return true;
        }
        String normalizedMemory = normalize(memoryText);
        return normalizedMemory.contains(anchor) || anchor.contains(stripMemoryPrefix(normalizedMemory));
    }

    private String stripMemoryPrefix(String memoryText) {
        return memoryText
                .replace("学生关注", "")
                .replace("学习记忆", "")
                .replace("：", "")
                .replace(":", "")
                .trim();
    }

    private String extractDefinitionTopic(String question) {
        String normalized = normalize(question);
        return normalized
                .replaceFirst("^什么是", "")
                .replaceFirst("^请解释", "")
                .replaceFirst("^解释一下", "")
                .replaceFirst("^介绍一下", "")
                .replaceAll("[？?。.!！]+$", "")
                .trim();
    }

    private boolean isExplicitContinuation(String question) {
        return question.contains("继续")
                || question.contains("复习")
                || question.contains("之前没懂")
                || question.contains("之前不懂")
                || question.contains("之前的问题")
                || question.contains("我关注的")
                || question.contains("上次");
    }

    private String normalize(String text) {
        return String.valueOf(text == null ? "" : text)
                .replaceAll("[\\r\\n\\t\\s]+", "")
                .trim();
    }

    private boolean hasText(String text) {
        return StringUtils.hasText(normalize(text));
    }

    private String firstText(String first, String second) {
        return hasText(first) ? first : second;
    }
}
