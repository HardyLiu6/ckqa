package org.ysu.ckqaback.qa;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.entity.QaMessages;
import org.ysu.ckqaback.entity.QaRetrievalLogs;
import org.ysu.ckqaback.entity.QaSessions;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.qa.dto.CreateQaMessageRequest;
import org.ysu.ckqaback.qa.dto.CreateQaSessionRequest;
import org.ysu.ckqaback.qa.dto.QaMessageResponse;
import org.ysu.ckqaback.qa.dto.QaTaskDetailResponse;
import org.ysu.ckqaback.qa.dto.QaTaskSubmissionResponse;
import org.ysu.ckqaback.qa.context.QaRetrievalLogContext;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import org.ysu.ckqaback.service.QaMessagesService;
import org.ysu.ckqaback.service.QaRetrievalLogsService;
import org.ysu.ckqaback.service.QaSessionsService;
import org.ysu.ckqaback.service.UsersService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class QaWorkflowServiceTest {

    @Test
    void shouldAllowSessionOwnerAccess() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                mock(QaMessagesService.class),
                mock(QaRetrievalLogsService.class),
                mock(KnowledgeBasesService.class),
                mock(UsersService.class),
                mock(QaTaskWorker.class),
                buildTaskPolicyProperties()
        );
        QaSessions session = new QaSessions();
        session.setId(5L);
        session.setUserId(7L);
        given(qaSessionsService.getRequiredById(5L)).willReturn(session);

        workflowService.ensureSessionOwner(5L, 7L);

        then(qaSessionsService).should().getRequiredById(5L);
    }

    @Test
    void shouldRejectSessionAccessForNonOwner() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                mock(QaMessagesService.class),
                mock(QaRetrievalLogsService.class),
                mock(KnowledgeBasesService.class),
                mock(UsersService.class),
                mock(QaTaskWorker.class),
                buildTaskPolicyProperties()
        );
        QaSessions session = new QaSessions();
        session.setId(5L);
        session.setUserId(9L);
        given(qaSessionsService.getRequiredById(5L)).willReturn(session);

        assertThatThrownBy(() -> workflowService.ensureSessionOwner(5L, 7L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(ApiResultCode.AUTH_FORBIDDEN.getCode());
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                })
                .hasMessageContaining("只能访问自己的问答会话");
    }

    @Test
    void shouldCreateFormalSessionWithLockedActiveIndexRunId() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        UsersService usersService = mock(UsersService.class);
        QaTaskWorker qaTaskWorker = mock(QaTaskWorker.class);

        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                usersService,
                qaTaskWorker,
                buildTaskPolicyProperties()
        );

        CreateQaSessionRequest request = new CreateQaSessionRequest();
        request.setUserId(7L);
        request.setCourseId("os");
        request.setKnowledgeBaseId(3L);
        request.setTitle("操作系统问答");

        QaSessions saved = new QaSessions();
        saved.setId(5L);
        saved.setSessionCode("qa-0001");
        saved.setUserId(7L);
        saved.setCourseId("os");
        saved.setKnowledgeBaseId(3L);
        saved.setSessionType("formal");
        saved.setTitle("操作系统问答");
        saved.setStatus("active");
        saved.setIndexRunId(17L);
        saved.setIndexLockedAt(LocalDateTime.of(2026, 5, 17, 10, 0));

        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(qaSessionsService.createSession(eq(request), eq(17L), any())).willReturn(saved);

        var response = workflowService.createSession(request);

        assertThat(response.getIndexRunId()).isEqualTo(17L);
        assertThat(response.getIndexLockedAt()).isEqualTo(LocalDateTime.of(2026, 5, 17, 10, 0));
    }

    @Test
    void shouldSubmitTaskAndDispatchWorkerWithoutAssistantMessage() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        UsersService usersService = mock(UsersService.class);
        QaTaskWorker qaTaskWorker = mock(QaTaskWorker.class);

        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                usersService,
                qaTaskWorker,
                buildTaskPolicyProperties()
        );

        QaSessions session = new QaSessions();
        session.setId(5L);
        session.setStatus("active");
        session.setKnowledgeBaseId(3L);
        session.setCourseId("os");
        session.setSessionType("formal");
        session.setIndexRunId(23L);

        QaMessages userMessage = new QaMessages();
        userMessage.setId(11L);
        userMessage.setSessionId(5L);
        userMessage.setRole("user");
        userMessage.setSequenceNo(1);
        userMessage.setContent("请概括这套图谱的主题");

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9001L);
        task.setTaskStatus("pending");
        task.setProgressStage("queued");

        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(qaMessagesService.listBySessionId(5L)).willReturn(List.of());
        given(qaMessagesService.appendUserMessage(5L, "请概括这套图谱的主题")).willReturn(userMessage);
        given(qaRetrievalLogsService.createPendingTask(
                eq(5L),
                eq("os"),
                eq(23L),
                eq(11L),
                eq("basic"),
                eq("请概括这套图谱的主题"),
                argThat(context -> "none".equals(context.contextStrategy())
                        && "请概括这套图谱的主题".equals(context.originalQueryText())
                        && "请概括这套图谱的主题".equals(context.retrievalQueryText()))
        )).willReturn(task);

        QaTaskSubmissionResponse response = workflowService.sendMessage(5L, new CreateQaMessageRequest("basic", "请概括这套图谱的主题"));

        assertThat(response.getTaskId()).isEqualTo(9001L);
        assertThat(response.getMode()).isEqualTo("basic");
        assertThat(response.getTaskStatus()).isEqualTo("pending");
        assertThat(response.getProgressStage()).isEqualTo("queued");
        assertThat(response.getRecommendedPollingIntervalSeconds()).isEqualTo(10);
        assertThat(response.getStaleTimeoutSeconds()).isEqualTo(300);
        assertThat(response.getTimeoutMessage()).contains("basic");
        assertThat(response.getContextApplied()).isFalse();
        assertThat(response.getContextStrategy()).isEqualTo("none");
        assertThat(response.getContextSizeEstimate().getChars()).isZero();
        then(qaTaskWorker).should().dispatch(5L, 9001L);
        then(qaMessagesService).should(never()).appendAssistantMessage(anyLong(), eq("请概括这套图谱的主题"));
    }

    @Test
    void shouldRewritePronounFollowUpBeforeCreatingPendingTask() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        UsersService usersService = mock(UsersService.class);
        QaTaskWorker qaTaskWorker = mock(QaTaskWorker.class);

        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                usersService,
                qaTaskWorker,
                buildTaskPolicyProperties()
        );

        QaSessions session = new QaSessions();
        session.setId(5L);
        session.setStatus("active");
        session.setKnowledgeBaseId(3L);
        session.setCourseId("os");
        session.setSessionType("formal");
        session.setIndexRunId(23L);

        QaMessages previousUser = message(101L, 5L, "user", 1, "什么是死锁？");
        QaMessages previousAssistant = message(102L, 5L, "assistant", 2, "死锁是多个进程互相等待资源的状态。");
        QaMessages userMessage = message(103L, 5L, "user", 3, "它和资源分配图有什么关系？");

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9002L);
        task.setTaskStatus("pending");
        task.setProgressStage("queued");

        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(qaMessagesService.listBySessionId(5L)).willReturn(List.of(previousUser, previousAssistant));
        given(qaMessagesService.appendUserMessage(5L, "它和资源分配图有什么关系？")).willReturn(userMessage);
        given(qaRetrievalLogsService.createPendingTask(
                eq(5L),
                eq("os"),
                eq(23L),
                eq(103L),
                eq("basic"),
                eq("关于上一轮主题「死锁」：它和资源分配图有什么关系？"),
                argThat(context -> "recent".equals(context.contextStrategy())
                        && context.rewriteApplied()
                        && "1-2".equals(context.rewriteSourceMessageRange())
                        && context.contextCharCount() > 0)
        )).willReturn(task);

        QaTaskSubmissionResponse response = workflowService.sendMessage(5L, new CreateQaMessageRequest("basic", "它和资源分配图有什么关系？"));

        assertThat(response.getTaskId()).isEqualTo(9002L);
        assertThat(response.getContextApplied()).isTrue();
        assertThat(response.getContextStrategy()).isEqualTo("recent");
        assertThat(response.getContextSizeEstimate().getChars()).isGreaterThan(0);
    }

    @Test
    void shouldBackfillLegacyFormalSessionIndexRunIdFromUniqueSuccessfulTask() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        UsersService usersService = mock(UsersService.class);
        QaTaskWorker qaTaskWorker = mock(QaTaskWorker.class);

        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                usersService,
                qaTaskWorker,
                buildTaskPolicyProperties()
        );

        QaSessions session = new QaSessions();
        session.setId(5L);
        session.setStatus("active");
        session.setKnowledgeBaseId(3L);
        session.setCourseId("os");
        session.setSessionType("formal");

        QaMessages userMessage = message(11L, 5L, "user", 1, "继续提问");
        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9003L);
        task.setTaskStatus("pending");
        task.setProgressStage("queued");

        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(qaRetrievalLogsService.findDistinctSuccessfulIndexRunIdsBySession(5L)).willReturn(List.of(18L));
        given(qaMessagesService.listBySessionId(5L)).willReturn(List.of());
        given(qaMessagesService.appendUserMessage(5L, "继续提问")).willReturn(userMessage);
        given(qaRetrievalLogsService.createPendingTask(
                eq(5L),
                eq("os"),
                eq(18L),
                eq(11L),
                eq("basic"),
                eq("继续提问"),
                any(QaRetrievalLogContext.class)
        )).willReturn(task);

        workflowService.sendMessage(5L, new CreateQaMessageRequest("basic", "继续提问"));

        then(qaSessionsService).should().lockIndexRun(eq(5L), eq(18L), any());
    }

    @Test
    void shouldRejectLegacyFormalSessionWhenIndexRunIdCannotBeBackfilled() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        UsersService usersService = mock(UsersService.class);
        QaTaskWorker qaTaskWorker = mock(QaTaskWorker.class);

        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                usersService,
                qaTaskWorker,
                buildTaskPolicyProperties()
        );

        QaSessions session = new QaSessions();
        session.setId(5L);
        session.setStatus("active");
        session.setKnowledgeBaseId(3L);
        session.setCourseId("os");
        session.setSessionType("formal");

        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(qaRetrievalLogsService.findDistinctSuccessfulIndexRunIdsBySession(5L)).willReturn(List.of(18L, 19L));

        assertThatThrownBy(() -> workflowService.sendMessage(5L, new CreateQaMessageRequest("basic", "继续提问")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("索引版本固化前");

        then(qaMessagesService).should(never()).appendUserMessage(anyLong(), any());
    }

    @Test
    void shouldDispatchWorkerAfterTransactionCommitWhenSynchronizationActive() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        UsersService usersService = mock(UsersService.class);
        QaTaskWorker qaTaskWorker = mock(QaTaskWorker.class);

        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                usersService,
                qaTaskWorker,
                buildTaskPolicyProperties()
        );

        QaSessions session = new QaSessions();
        session.setId(5L);
        session.setStatus("active");
        session.setKnowledgeBaseId(3L);
        session.setCourseId("os");
        session.setSessionType("formal");
        session.setIndexRunId(17L);

        QaMessages userMessage = new QaMessages();
        userMessage.setId(11L);
        userMessage.setSessionId(5L);
        userMessage.setRole("user");
        userMessage.setSequenceNo(1);
        userMessage.setContent("请概括这套图谱的主题");

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9001L);
        task.setTaskStatus("pending");
        task.setProgressStage("queued");

        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(qaMessagesService.listBySessionId(5L)).willReturn(List.of());
        given(qaMessagesService.appendUserMessage(5L, "请概括这套图谱的主题")).willReturn(userMessage);
        given(qaRetrievalLogsService.createPendingTask(eq(5L), eq("os"), eq(17L), eq(11L), eq("basic"), eq("请概括这套图谱的主题"), any(QaRetrievalLogContext.class))).willReturn(task);

        TransactionSynchronizationManager.initSynchronization();
        try {
            workflowService.sendMessage(5L, new CreateQaMessageRequest("basic", "请概括这套图谱的主题"));

            then(qaTaskWorker).should(never()).dispatch(5L, 9001L);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCommit());

            then(qaTaskWorker).should().dispatch(5L, 9001L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldReturnModeSpecificPollingHintsForDriftSubmission() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        UsersService usersService = mock(UsersService.class);
        QaTaskWorker qaTaskWorker = mock(QaTaskWorker.class);

        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                usersService,
                qaTaskWorker,
                buildTaskPolicyProperties()
        );

        QaSessions session = new QaSessions();
        session.setId(5L);
        session.setStatus("active");
        session.setKnowledgeBaseId(3L);
        session.setCourseId("os");
        session.setSessionType("formal");
        session.setIndexRunId(17L);

        QaMessages userMessage = new QaMessages();
        userMessage.setId(11L);
        userMessage.setSessionId(5L);
        userMessage.setRole("user");
        userMessage.setSequenceNo(1);
        userMessage.setContent("请用 drift 模式回答");

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9001L);
        task.setTaskStatus("pending");
        task.setProgressStage("queued");

        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(qaMessagesService.listBySessionId(5L)).willReturn(List.of());
        given(qaMessagesService.appendUserMessage(5L, "请用 drift 模式回答")).willReturn(userMessage);
        given(qaRetrievalLogsService.createPendingTask(eq(5L), eq("os"), eq(17L), eq(11L), eq("drift"), eq("请用 drift 模式回答"), any(QaRetrievalLogContext.class))).willReturn(task);

        QaTaskSubmissionResponse response = workflowService.sendMessage(5L, new CreateQaMessageRequest("drift", "请用 drift 模式回答"));

        assertThat(response.getMode()).isEqualTo("drift");
        assertThat(response.getRecommendedPollingIntervalSeconds()).isEqualTo(15);
        assertThat(response.getStaleTimeoutSeconds()).isEqualTo(1800);
        assertThat(response.getTimeoutMessage()).contains("drift");
    }

    @Test
    void shouldListMessagesWithTaskSummaryOnlyOnUserMessages() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        UsersService usersService = mock(UsersService.class);
        QaTaskWorker qaTaskWorker = mock(QaTaskWorker.class);

        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                usersService,
                qaTaskWorker,
                buildTaskPolicyProperties()
        );

        QaSessions session = new QaSessions();
        session.setId(5L);

        QaMessages userMessage = new QaMessages();
        userMessage.setId(101L);
        userMessage.setSessionId(5L);
        userMessage.setRole("user");
        userMessage.setSequenceNo(1);
        userMessage.setContent("请概括这套图谱的主题");
        userMessage.setCreatedAt(LocalDateTime.of(2026, 4, 22, 20, 0));

        QaMessages assistantMessage = new QaMessages();
        assistantMessage.setId(102L);
        assistantMessage.setSessionId(5L);
        assistantMessage.setRole("assistant");
        assistantMessage.setSequenceNo(2);
        assistantMessage.setContent("图谱主题集中在操作系统概念网络");
        assistantMessage.setCreatedAt(LocalDateTime.of(2026, 4, 22, 20, 1));

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9001L);
        task.setUserMessageId(101L);
        task.setTaskStatus("running");
        task.setProgressStage("running");

        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(qaMessagesService.listBySessionId(5L)).willReturn(List.of(userMessage, assistantMessage));
        given(qaRetrievalLogsService.findLatestByUserMessageIds(List.of(101L))).willReturn(Map.of(101L, task));

        List<QaMessageResponse> responses = workflowService.listMessages(5L);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getTaskStatus()).isEqualTo("running");
        assertThat(responses.get(0).getProgressStage()).isEqualTo("running");
        assertThat(responses.get(1).getTaskStatus()).isNull();
        assertThat(responses.get(1).getProgressStage()).isNull();
    }

    @Test
    void shouldReturnModeSpecificPollingHintsForTaskDetail() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        UsersService usersService = mock(UsersService.class);
        QaTaskWorker qaTaskWorker = mock(QaTaskWorker.class);

        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                usersService,
                qaTaskWorker,
                buildTaskPolicyProperties()
        );

        QaSessions session = new QaSessions();
        session.setId(5L);

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9001L);
        task.setSessionId(5L);
        task.setUserMessageId(101L);
        task.setTaskStatus("running");
        task.setProgressStage("running");
        task.setQueryMode("drift");
        task.setQueryText("请用 drift 模式回答");
        task.setLatestLogs("drift still running");

        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(qaRetrievalLogsService.getRequiredTask(5L, 9001L)).willReturn(task);

        QaTaskDetailResponse response = workflowService.getTaskDetail(5L, 9001L);

        assertThat(response.getMode()).isEqualTo("drift");
        assertThat(response.getRecommendedPollingIntervalSeconds()).isEqualTo(15);
        assertThat(response.getStaleTimeoutSeconds()).isEqualTo(1800);
        assertThat(response.getTimeoutMessage()).contains("drift");
    }

    private KnowledgeBases buildKnowledgeBase() {
        KnowledgeBases knowledgeBase = new KnowledgeBases();
        knowledgeBase.setId(3L);
        knowledgeBase.setCourseId("os");
        knowledgeBase.setActiveIndexRunId(17L);
        return knowledgeBase;
    }

    private QaMessages message(Long id, Long sessionId, String role, int sequenceNo, String content) {
        QaMessages message = new QaMessages();
        message.setId(id);
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setSequenceNo(sequenceNo);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.of(2026, 5, 17, 12, sequenceNo));
        return message;
    }

    private CkqaIntegrationProperties buildTaskPolicyProperties() {
        CkqaIntegrationProperties properties = new CkqaIntegrationProperties();
        properties.getPolling().getQueryTaskModeIntervalSeconds().put("drift", 15L);
        properties.getTimeout().getQueryTaskModeStaleSeconds().put("drift", 1800L);
        return properties;
    }
}
