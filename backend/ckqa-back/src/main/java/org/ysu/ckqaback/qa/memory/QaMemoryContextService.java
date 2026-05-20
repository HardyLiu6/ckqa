package org.ysu.ckqaback.qa.memory;

import org.springframework.beans.factory.annotation.Autowired;
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
public class QaMemoryContextService {

    private static final int MAX_RECENT_MESSAGES = 6;
    private static final int MAX_MEMORY_ITEMS = 3;
    private static final int MAX_MEMORY_CHARS = 1000;
    private static final int MAX_HISTORY_CHARS = 3000;
    private static final String MEMORY_PREFIX = "学习记忆（仅作解释偏好或学习关注点，不作为课程事实，也不能覆盖当前会话指代）：";

    private final QaMemoryPreferencesService preferencesService;
    private final QaLearningMemoriesService memoriesService;
    private final QaMemoryInjectionRouter injectionRouter;

    public QaMemoryContextService(QaMemoryPreferencesService preferencesService, QaLearningMemoriesService memoriesService) {
        this(preferencesService, memoriesService, new QaMemoryInjectionRouter());
    }

    @Autowired
    public QaMemoryContextService(
            QaMemoryPreferencesService preferencesService,
            QaLearningMemoriesService memoriesService,
            QaMemoryInjectionRouter injectionRouter
    ) {
        this.preferencesService = preferencesService;
        this.memoriesService = memoriesService;
        this.injectionRouter = injectionRouter;
    }

    public QaMemoryContextResult buildContext(String mode, String memoryPolicy, QaSessions session, List<QaMessages> sessionMessages) {
        return buildContext(mode, memoryPolicy, session, sessionMessages, "", "");
    }

    public QaMemoryContextResult buildContext(
            String mode,
            String memoryPolicy,
            QaSessions session,
            List<QaMessages> sessionMessages,
            String question,
            String latestTopic
    ) {
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

        QaMemoryInjectionDecision decision = injectionRouter.decide(question, latestTopic);
        List<GraphRagConversationMessage> longMemory = new ArrayList<>();
        int memoryChars = 0;
        for (QaLearningMemories memory : memoriesService.listActiveByScope(
                session.getUserId(),
                session.getCourseId(),
                session.getKnowledgeBaseId(),
                session.getIndexRunId(),
                MAX_MEMORY_ITEMS
        )) {
            if (!"active".equals(memory.getStatus()) || !StringUtils.hasText(memory.getMemoryText())) {
                continue;
            }
            if (!injectionRouter.shouldInclude(decision, memory, question, latestTopic)) {
                continue;
            }
            String content = MEMORY_PREFIX + memory.getMemoryText().trim();
            int contentChars = content.length();
            if (memoryChars + contentChars > MAX_MEMORY_CHARS) {
                continue;
            }
            longMemory.add(new GraphRagConversationMessage("assistant", content));
            memoryChars += contentChars;
        }

        List<GraphRagConversationMessage> recentHistory = new ArrayList<>();
        for (QaMessages message : recentMessages(sessionMessages)) {
            if (!isHistoryRole(message.getRole()) || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            recentHistory.add(new GraphRagConversationMessage(message.getRole(), message.getContent().trim()));
        }

        List<GraphRagConversationMessage> history = new ArrayList<>(longMemory);
        history.addAll(recentHistory);

        history = trimToBudget(history);
        if (history.isEmpty()) {
            return QaMemoryContextResult.notApplied("history_empty");
        }
        String strategy = resolveStrategy(decision, longMemory);
        return new QaMemoryContextResult(
                true,
                strategy,
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

    private String resolveStrategy(QaMemoryInjectionDecision decision, List<GraphRagConversationMessage> longMemory) {
        if (longMemory == null || longMemory.isEmpty()) {
            return "local_history_short_only";
        }
        if (decision != null && "relevant_memory".equals(decision.longMemoryMode())) {
            return "local_history_relevant_memory";
        }
        return "local_history_preference_only";
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
