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
        saveSummary(sessionId, window, result);
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
            QaRetrievalLogs task = taskMap.get(message.getId());
            if (task == null || !"success".equals(task.getTaskStatus()) || task.getAssistantMessageId() == null) {
                break;
            }
            QaMessages assistant = byId.get(task.getAssistantMessageId());
            if (assistant == null
                    || !"assistant".equals(assistant.getRole())
                    || assistant.getSequenceNo() == null
                    || assistant.getSequenceNo() <= message.getSequenceNo()) {
                break;
            }
            included.add(message);
            included.add(assistant);
            charCount += lengthOf(message.getContent()) + lengthOf(assistant.getContent());
            lastSequenceNo = assistant.getSequenceNo();
        }

        return new CompletedWindow(included, charCount, lastSequenceNo);
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

    private void saveSummary(Long sessionId, CompletedWindow window, QaSummaryResult result) {
        LocalDateTime now = LocalDateTime.now(SHANGHAI_ZONE);
        QaSessionSummaries summary = new QaSessionSummaries();
        summary.setSessionId(sessionId);
        summary.setSummaryUntilSequenceNo(window.lastSequenceNo());
        summary.setSourceMessageCount(window.messages().size());
        summary.setCreatedAt(now);
        summary.setUpdatedAt(now);
        if (result.success()) {
            summary.setStatus("success");
            summary.setSummaryText(truncate(result.summaryText(), maxChars));
        } else {
            summary.setStatus("failed");
            summary.setErrorMessage(truncate(result.errorMessage(), 500));
        }
        summariesService.save(summary);
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
