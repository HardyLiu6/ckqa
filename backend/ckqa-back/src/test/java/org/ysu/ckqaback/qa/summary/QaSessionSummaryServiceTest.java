package org.ysu.ckqaback.qa.summary;

import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.QaMessages;
import org.ysu.ckqaback.entity.QaRetrievalLogs;
import org.ysu.ckqaback.entity.QaSessionSummaries;
import org.ysu.ckqaback.service.QaMessagesService;
import org.ysu.ckqaback.service.QaRetrievalLogsService;
import org.ysu.ckqaback.service.QaSessionSummariesService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class QaSessionSummaryServiceTest {

    @Test
    void shouldCreateSummaryWhenUnsummarizedCompletedMessagesReachThreshold() {
        QaMessagesService messagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService retrievalLogsService = mock(QaRetrievalLogsService.class);
        QaSessionSummariesService summariesService = mock(QaSessionSummariesService.class);
        StubSummaryClient summaryClient = new StubSummaryClient("本会话已讨论死锁定义和资源分配图。");
        QaSessionSummaryService service = new QaSessionSummaryService(
                messagesService,
                retrievalLogsService,
                summariesService,
                summaryClient,
                Runnable::run,
                true,
                12,
                3000,
                800
        );

        List<QaMessages> messages = completedConversation(6);
        given(messagesService.listBySessionId(5L)).willReturn(messages);
        given(summariesService.findLatestSuccessfulBySessionId(5L)).willReturn(null);
        given(retrievalLogsService.findLatestByUserMessageIds(List.of(1L, 3L, 5L, 7L, 9L, 11L)))
                .willReturn(successTaskMap(messages));

        service.checkAndSummarizeAsync(5L);

        assertThat(summaryClient.requestedText()).contains("学生：问题 1", "助手：回答 6");
        then(summariesService).should().save(argThat(summary ->
                summary.getSessionId().equals(5L)
                        && "success".equals(summary.getStatus())
                        && summary.getSummaryUntilSequenceNo() == 12
                        && summary.getSourceMessageCount() == 12
                        && summary.getSummaryText().equals("本会话已讨论死锁定义和资源分配图。")
        ));
    }

    @Test
    void shouldNotTriggerWhenBelowThreshold() {
        QaMessagesService messagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService retrievalLogsService = mock(QaRetrievalLogsService.class);
        QaSessionSummariesService summariesService = mock(QaSessionSummariesService.class);
        StubSummaryClient summaryClient = new StubSummaryClient("不会调用");
        QaSessionSummaryService service = new QaSessionSummaryService(
                messagesService,
                retrievalLogsService,
                summariesService,
                summaryClient,
                Runnable::run,
                true,
                12,
                3000,
                800
        );

        List<QaMessages> messages = completedConversation(2);
        given(messagesService.listBySessionId(5L)).willReturn(messages);
        given(summariesService.findLatestSuccessfulBySessionId(5L)).willReturn(null);
        given(retrievalLogsService.findLatestByUserMessageIds(List.of(1L, 3L))).willReturn(successTaskMap(messages));

        service.checkAndSummarizeAsync(5L);

        assertThat(summaryClient.callCount()).isZero();
        then(summariesService).should(never()).save(argThat(summary -> true));
    }

    @Test
    void shouldStopWatermarkBeforePendingUserMessage() {
        QaMessagesService messagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService retrievalLogsService = mock(QaRetrievalLogsService.class);
        QaSessionSummariesService summariesService = mock(QaSessionSummariesService.class);
        StubSummaryClient summaryClient = new StubSummaryClient("不会调用");
        QaSessionSummaryService service = new QaSessionSummaryService(
                messagesService,
                retrievalLogsService,
                summariesService,
                summaryClient,
                Runnable::run,
                true,
                4,
                100,
                800
        );

        List<QaMessages> messages = completedConversation(3);
        QaRetrievalLogs success = successTask(messages.get(0), messages.get(1));
        QaRetrievalLogs pending = successTask(messages.get(2), messages.get(3));
        pending.setTaskStatus("pending");
        given(messagesService.listBySessionId(5L)).willReturn(messages);
        given(summariesService.findLatestSuccessfulBySessionId(5L)).willReturn(null);
        given(retrievalLogsService.findLatestByUserMessageIds(List.of(1L, 3L, 5L)))
                .willReturn(Map.of(1L, success, 3L, pending));

        service.checkAndSummarizeAsync(5L);

        assertThat(summaryClient.callCount()).isZero();
        then(summariesService).should(never()).save(argThat(summary -> true));
    }

    @Test
    void shouldPersistFailedSummaryWhenClientFails() {
        QaMessagesService messagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService retrievalLogsService = mock(QaRetrievalLogsService.class);
        QaSessionSummariesService summariesService = mock(QaSessionSummariesService.class);
        StubSummaryClient summaryClient = new StubSummaryClient(null);
        QaSessionSummaryService service = new QaSessionSummaryService(
                messagesService,
                retrievalLogsService,
                summariesService,
                summaryClient,
                Runnable::run,
                true,
                4,
                100,
                800
        );

        List<QaMessages> messages = completedConversation(2);
        given(messagesService.listBySessionId(5L)).willReturn(messages);
        given(summariesService.findLatestSuccessfulBySessionId(5L)).willReturn(null);
        given(retrievalLogsService.findLatestByUserMessageIds(List.of(1L, 3L))).willReturn(successTaskMap(messages));

        service.checkAndSummarizeAsync(5L);

        then(summariesService).should().save(argThat(summary ->
                "failed".equals(summary.getStatus())
                        && summary.getErrorMessage().contains("summary client failed")
                        && summary.getSummaryUntilSequenceNo() == 4
        ));
    }

    private List<QaMessages> completedConversation(int pairCount) {
        List<QaMessages> messages = new ArrayList<>();
        for (int index = 1; index <= pairCount; index++) {
            int userSequence = index * 2 - 1;
            int assistantSequence = index * 2;
            messages.add(message((long) userSequence, "user", userSequence, "问题 " + index));
            messages.add(message((long) assistantSequence, "assistant", assistantSequence, "回答 " + index));
        }
        return messages;
    }

    private Map<Long, QaRetrievalLogs> successTaskMap(List<QaMessages> messages) {
        return messages.stream()
                .filter(message -> "user".equals(message.getRole()))
                .collect(java.util.stream.Collectors.toMap(
                        QaMessages::getId,
                        user -> successTask(user, messages.stream()
                                .filter(candidate -> candidate.getSequenceNo().equals(user.getSequenceNo() + 1))
                                .findFirst()
                                .orElseThrow())
                ));
    }

    private QaRetrievalLogs successTask(QaMessages user, QaMessages assistant) {
        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setUserMessageId(user.getId());
        task.setAssistantMessageId(assistant.getId());
        task.setTaskStatus("success");
        return task;
    }

    private QaMessages message(Long id, String role, int sequenceNo, String content) {
        QaMessages message = new QaMessages();
        message.setId(id);
        message.setSessionId(5L);
        message.setRole(role);
        message.setSequenceNo(sequenceNo);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.of(2026, 5, 17, 12, sequenceNo));
        return message;
    }

    private static final class StubSummaryClient implements QaSummaryClientPort {
        private final String responseText;
        private int callCount;
        private String requestedText;

        private StubSummaryClient(String responseText) {
            this.responseText = responseText;
        }

        @Override
        public QaSummaryResult summarize(String previousSummary, String conversationText) {
            callCount++;
            requestedText = conversationText;
            if (responseText == null) {
                return QaSummaryResult.failure("summary client failed");
            }
            return QaSummaryResult.success(responseText);
        }

        int callCount() {
            return callCount;
        }

        String requestedText() {
            return requestedText;
        }
    }
}
