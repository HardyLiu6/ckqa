package org.ysu.ckqaback.qa.memory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.entity.QaLearningMemories;
import org.ysu.ckqaback.entity.QaMemoryPreferences;
import org.ysu.ckqaback.entity.QaMessages;
import org.ysu.ckqaback.entity.QaSessions;
import org.ysu.ckqaback.integration.graphrag.GraphRagConversationMessage;
import org.ysu.ckqaback.service.QaLearningMemoriesService;
import org.ysu.ckqaback.service.QaMemoryPreferencesService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 组装 local 模式可选使用的长期记忆上下文。
 */
@Service
@RequiredArgsConstructor
public class QaMemoryContextService {

    private static final int MAX_RECENT_MESSAGES = 6;
    private static final int MAX_MEMORY_ITEMS = 3;
    private static final int MAX_MEMORY_CHARS = 1000;
    private static final int MAX_HISTORY_CHARS = 3000;

    private final QaMemoryPreferencesService preferencesService;
    private final QaLearningMemoriesService memoriesService;

    public QaMemoryContextResult buildContext(String mode, String memoryPolicy, QaSessions session, List<QaMessages> sessionMessages) {
        String policy = normalizePolicy(memoryPolicy);
        if (!"local".equals(mode)) {
            return QaMemoryContextResult.notApplied("mode_not_local");
        }
        if ("off".equals(policy)) {
            return QaMemoryContextResult.notApplied("policy_off");
        }
        if (session == null || session.getUserId() == null || !StringUtils.hasText(session.getCourseId())
                || session.getKnowledgeBaseId() == null || session.getIndexRunId() == null) {
            return QaMemoryContextResult.notApplied("scope_incomplete");
        }

        QaMemoryPreferences preference = preferencesService.findByScope(
                session.getUserId(),
                session.getCourseId(),
                session.getKnowledgeBaseId(),
                session.getIndexRunId()
        );
        if (preference == null || !Boolean.TRUE.equals(preference.getEnabled())) {
            return QaMemoryContextResult.notApplied("preference_disabled");
        }

        List<GraphRagConversationMessage> history = new ArrayList<>();
        for (QaMessages message : recentMessages(sessionMessages)) {
            if (!isHistoryRole(message.getRole()) || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            history.add(new GraphRagConversationMessage(message.getRole(), message.getContent().trim()));
        }

        int historyChars = charCount(history);
        int memoryChars = 0;
        for (QaLearningMemories memory : memoriesService.listActiveByScope(
                session.getUserId(),
                session.getCourseId(),
                session.getKnowledgeBaseId(),
                session.getIndexRunId(),
                MAX_MEMORY_ITEMS
        )) {
            if (!StringUtils.hasText(memory.getMemoryText())) {
                continue;
            }
            String content = "学习记忆：" + memory.getMemoryText().trim();
            int contentChars = content.length();
            if (memoryChars + contentChars > MAX_MEMORY_CHARS || historyChars + contentChars > MAX_HISTORY_CHARS) {
                continue;
            }
            history.add(new GraphRagConversationMessage("assistant", content));
            memoryChars += contentChars;
            historyChars += contentChars;
        }

        history = trimToBudget(history);
        if (history.isEmpty()) {
            return QaMemoryContextResult.notApplied("history_empty");
        }
        return new QaMemoryContextResult(
                true,
                "local_history",
                scope(session),
                history.size(),
                charCount(history),
                history,
                null
        );
    }

    private String normalizePolicy(String memoryPolicy) {
        return StringUtils.hasText(memoryPolicy) ? memoryPolicy.trim() : "default";
    }

    private List<QaMessages> recentMessages(List<QaMessages> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<QaMessages> ordered = messages.stream()
                .filter(message -> message != null && isHistoryRole(message.getRole()))
                .sorted(Comparator.comparing(
                        QaMessages::getSequenceNo,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .toList();
        int fromIndex = Math.max(0, ordered.size() - MAX_RECENT_MESSAGES);
        return ordered.subList(fromIndex, ordered.size());
    }

    private List<GraphRagConversationMessage> trimToBudget(List<GraphRagConversationMessage> history) {
        List<GraphRagConversationMessage> trimmed = new ArrayList<>(history);
        while (charCount(trimmed) > MAX_HISTORY_CHARS && !trimmed.isEmpty()) {
            trimmed.remove(0);
        }
        return trimmed;
    }

    private int charCount(List<GraphRagConversationMessage> history) {
        return history.stream()
                .map(GraphRagConversationMessage::content)
                .filter(StringUtils::hasText)
                .mapToInt(String::length)
                .sum();
    }

    private boolean isHistoryRole(String role) {
        return "user".equals(role) || "assistant".equals(role);
    }

    private String scope(QaSessions session) {
        return "userId=" + session.getUserId()
                + ";courseId=" + session.getCourseId()
                + ";knowledgeBaseId=" + session.getKnowledgeBaseId()
                + ";indexRunId=" + session.getIndexRunId();
    }
}
