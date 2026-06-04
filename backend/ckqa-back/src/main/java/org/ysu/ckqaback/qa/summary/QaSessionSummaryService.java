package org.ysu.ckqaback.qa.summary;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.entity.QaMessages;
import org.ysu.ckqaback.entity.QaRetrievalLogs;
import org.ysu.ckqaback.entity.QaSessionSummaries;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.qa.context.QaContextSummary;
import org.ysu.ckqaback.qa.context.SessionSemanticState;
import org.ysu.ckqaback.qa.context.QaTopicResolver;
import org.ysu.ckqaback.qa.context.QaTopicStack;
import org.ysu.ckqaback.service.QaMessagesService;
import org.ysu.ckqaback.service.QaRetrievalLogsService;
import org.ysu.ckqaback.service.QaSessionSummariesService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 会话滚动摘要触发器。摘要失败只落诊断记录，不阻断问答主链路。
 */
@Service
public class QaSessionSummaryService {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_CONVERSATION_TEXT_CHARS = 6000;

    private final QaMessagesService messagesService;
    private final QaRetrievalLogsService retrievalLogsService;
    private final QaSessionSummariesService summariesService;
    private final QaSummaryClientPort summaryClient;
    private final TaskExecutor executor;
    private final QaTopicResolver topicResolver = new QaTopicResolver();
    private final boolean enabled;
    private final int triggerMessageCount;
    private final int triggerCharCount;
    private final int maxChars;

    @Autowired
    public QaSessionSummaryService(
            QaMessagesService messagesService,
            QaRetrievalLogsService retrievalLogsService,
            QaSessionSummariesService summariesService,
            QaSummaryClientPort summaryClient,
            @Qualifier("qaTaskExecutor") TaskExecutor executor,
            CkqaIntegrationProperties properties
    ) {
        this(
                messagesService,
                retrievalLogsService,
                summariesService,
                summaryClient,
                executor,
                properties.getSummary().isEnabled(),
                properties.getSummary().getTriggerMessageCount(),
                properties.getSummary().getTriggerCharCount(),
                properties.getSummary().getMaxChars()
        );
    }

    public QaSessionSummaryService(
            QaMessagesService messagesService,
            QaRetrievalLogsService retrievalLogsService,
            QaSessionSummariesService summariesService,
            QaSummaryClientPort summaryClient,
            TaskExecutor executor,
            boolean enabled,
            int triggerMessageCount,
            int triggerCharCount,
            int maxChars
    ) {
        this.messagesService = messagesService;
        this.retrievalLogsService = retrievalLogsService;
        this.summariesService = summariesService;
        this.summaryClient = summaryClient;
        this.executor = executor;
        this.enabled = enabled;
        this.triggerMessageCount = triggerMessageCount > 0 ? triggerMessageCount : 12;
        this.triggerCharCount = triggerCharCount > 0 ? triggerCharCount : 3000;
        this.maxChars = maxChars > 0 ? maxChars : 800;
    }

    public void checkAndSummarizeAsync(Long sessionId) {
        if (!enabled || sessionId == null) {
            return;
        }
        executor.execute(() -> {
            try {
                checkAndSummarize(sessionId);
            } catch (RuntimeException ignored) {
                // 摘要是旁路增强，任何异常都不影响主问答任务成功状态。
            }
        });
    }

    private void checkAndSummarize(Long sessionId) {
        QaSessionSummaries latestSummary = summariesService.findLatestSuccessfulBySessionId(sessionId);
        int watermark = latestSummary == null || latestSummary.getSummaryUntilSequenceNo() == null
                ? 0
                : latestSummary.getSummaryUntilSequenceNo();

        List<QaMessages> messages = messagesService.listBySessionId(sessionId).stream()
                .filter(message -> "user".equals(message.getRole()) || "assistant".equals(message.getRole()))
                .filter(message -> message.getSequenceNo() != null && message.getSequenceNo() > watermark)
                .sorted(Comparator.comparing(QaMessages::getSequenceNo))
                .toList();
        if (messages.isEmpty()) {
            return;
        }

        List<Long> userMessageIds = messages.stream()
                .filter(message -> "user".equals(message.getRole()))
                .map(QaMessages::getId)
                .toList();
        Map<Long, QaRetrievalLogs> taskMap = retrievalLogsService.findLatestByUserMessageIds(userMessageIds);
        CompletedWindow window = continuousCompletedWindow(messages, taskMap);
        if (window.messages().isEmpty() || !shouldTrigger(window)) {
            return;
        }

        String previousSummary = latestSummary == null ? "" : latestSummary.getSummaryText();
        QaSummaryResult result = summaryClient.summarize(previousSummary, buildConversationText(window.messages()));
        saveSummary(sessionId, window, result, latestSummary);
    }

    private CompletedWindow continuousCompletedWindow(List<QaMessages> messages, Map<Long, QaRetrievalLogs> taskMap) {
        Map<Long, QaMessages> byId = messages.stream()
                .filter(message -> message.getId() != null)
                .collect(java.util.stream.Collectors.toMap(QaMessages::getId, message -> message, (left, right) -> left));
        List<QaMessages> included = new ArrayList<>();
        int charCount = 0;
        int lastSequenceNo = 0;

        for (QaMessages message : messages) {
            if (!"user".equals(message.getRole())) {
                continue;
            }
            QaMessages assistant = isCopiedMessage(message)
                    ? findFollowingCopiedAssistant(message, messages)
                    : findTaskAssistant(message, taskMap, byId);
            if (assistant == null) {
                break;
            }
            included.add(message);
            included.add(assistant);
            charCount += lengthOf(message.getContent()) + lengthOf(assistant.getContent());
            lastSequenceNo = assistant.getSequenceNo();
        }

        return new CompletedWindow(included, charCount, lastSequenceNo);
    }

    private QaMessages findTaskAssistant(
            QaMessages userMessage,
            Map<Long, QaRetrievalLogs> taskMap,
            Map<Long, QaMessages> byId
    ) {
        QaRetrievalLogs task = taskMap.get(userMessage.getId());
        if (task == null || !"success".equals(task.getTaskStatus()) || task.getAssistantMessageId() == null) {
            return null;
        }
        QaMessages assistant = byId.get(task.getAssistantMessageId());
        if (assistant == null
                || !"assistant".equals(assistant.getRole())
                || assistant.getSequenceNo() == null
                || userMessage.getSequenceNo() == null
                || assistant.getSequenceNo() <= userMessage.getSequenceNo()) {
            return null;
        }
        return assistant;
    }

    private QaMessages findFollowingCopiedAssistant(QaMessages userMessage, List<QaMessages> messages) {
        if (userMessage.getSequenceNo() == null) {
            return null;
        }
        for (QaMessages candidate : messages) {
            if (candidate.getSequenceNo() == null || candidate.getSequenceNo() <= userMessage.getSequenceNo()) {
                continue;
            }
            if ("assistant".equals(candidate.getRole()) && isCopiedMessage(candidate)) {
                return candidate;
            }
            return null;
        }
        return null;
    }

    private boolean isCopiedMessage(QaMessages message) {
        return message != null && message.getCopiedFromMessageId() != null;
    }

    private boolean shouldTrigger(CompletedWindow window) {
        return window.messages().size() >= triggerMessageCount || window.charCount() >= triggerCharCount;
    }

    private String buildConversationText(List<QaMessages> messages) {
        StringBuilder builder = new StringBuilder();
        for (QaMessages message : messages) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append("assistant".equals(message.getRole()) ? "助手：" : "学生：")
                    .append(trimToEmpty(message.getContent()));
            if (builder.length() > MAX_CONVERSATION_TEXT_CHARS) {
                builder.setLength(MAX_CONVERSATION_TEXT_CHARS);
                break;
            }
        }
        return builder.toString();
    }

    private void saveSummary(Long sessionId, CompletedWindow window, QaSummaryResult result, QaSessionSummaries previousSummary) {
        LocalDateTime now = LocalDateTime.now(SHANGHAI_ZONE);
        QaSessionSummaries summary = new QaSessionSummaries();
        summary.setSessionId(sessionId);
        summary.setSummaryUntilSequenceNo(window.lastSequenceNo());
        summary.setSourceMessageCount(window.messages().size());
        summary.setModel(result.model());
        summary.setDurationMs(result.durationMs());
        summary.setInputCharCount(result.inputCharCount());
        summary.setOutputCharCount(result.outputCharCount());
        summary.setCreatedAt(now);
        summary.setUpdatedAt(now);
        if (result.success()) {
            summary.setStatus("success");
            summary.setSummaryText(truncate(result.summaryText(), maxChars));
            QaTopicStack topicStack = topicResolver.resolveFromHistory(window.messages(), toContextSummary(previousSummary));
            summary.setLatestTopic(topicStack.latestTopic());
            summary.setLatestTopicMessageRange(topicStack.latestTopicMessageRange());
            summary.setActiveTopicsJson(topicStack.activeTopicsJson());
            QaContextSummary currentSummary = new QaContextSummary(
                    summary.getSummaryText(),
                    window.lastSequenceNo(),
                    topicStack.latestTopic(),
                    topicStack.latestTopicMessageRange(),
                    topicStack.activeTopicsJson()
            );
            SessionSemanticState semanticState = SessionSemanticState.from(topicStack, currentSummary, previousSummary != null);
            summary.setSemanticStateVersion(semanticState.version());
            summary.setSemanticStateJson(semanticState.json());
        } else {
            summary.setStatus("failed");
            summary.setErrorMessage(truncate(result.errorMessage(), 500));
        }
        summariesService.save(summary);
    }

    private QaContextSummary toContextSummary(QaSessionSummaries summary) {
        if (summary == null) {
            return null;
        }
        return new QaContextSummary(
                summary.getSummaryText(),
                summary.getSummaryUntilSequenceNo() == null ? 0 : summary.getSummaryUntilSequenceNo(),
                summary.getLatestTopic(),
                summary.getLatestTopicMessageRange(),
                summary.getActiveTopicsJson(),
                summary.getSemanticStateVersion(),
                summary.getSemanticStateJson()
        );
    }

    private int lengthOf(String value) {
        return value == null ? 0 : value.length();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private record CompletedWindow(List<QaMessages> messages, int charCount, int lastSequenceNo) {
    }
}
