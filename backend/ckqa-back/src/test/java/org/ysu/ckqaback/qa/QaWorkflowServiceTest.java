package org.ysu.ckqaback.qa;

import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.entity.QaMessages;
import org.ysu.ckqaback.entity.QaRetrievalLogs;
import org.ysu.ckqaback.entity.QaSessions;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.qa.dto.CreateQaMessageRequest;
import org.ysu.ckqaback.qa.dto.QaMessageResponse;
import org.ysu.ckqaback.qa.dto.QaTaskDetailResponse;
import org.ysu.ckqaback.qa.dto.QaTaskSubmissionResponse;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import org.ysu.ckqaback.service.QaMessagesService;
import org.ysu.ckqaback.service.QaRetrievalLogsService;
import org.ysu.ckqaback.service.QaSessionsService;
import org.ysu.ckqaback.service.UsersService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class QaWorkflowServiceTest {

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
        given(qaMessagesService.appendUserMessage(5L, "请概括这套图谱的主题")).willReturn(userMessage);
        given(qaRetrievalLogsService.createPendingTask(5L, "os", 17L, 11L, "basic", "请概括这套图谱的主题")).willReturn(task);

        QaTaskSubmissionResponse response = workflowService.sendMessage(5L, new CreateQaMessageRequest("basic", "请概括这套图谱的主题"));

        assertThat(response.getTaskId()).isEqualTo(9001L);
        assertThat(response.getMode()).isEqualTo("basic");
        assertThat(response.getTaskStatus()).isEqualTo("pending");
        assertThat(response.getProgressStage()).isEqualTo("queued");
        assertThat(response.getRecommendedPollingIntervalSeconds()).isEqualTo(10);
        assertThat(response.getStaleTimeoutSeconds()).isEqualTo(300);
        assertThat(response.getTimeoutMessage()).contains("basic");
        then(qaTaskWorker).should().dispatch(5L, 9001L);
        then(qaMessagesService).should(never()).appendAssistantMessage(anyLong(), eq("请概括这套图谱的主题"));
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
        given(qaMessagesService.appendUserMessage(5L, "请用 drift 模式回答")).willReturn(userMessage);
        given(qaRetrievalLogsService.createPendingTask(5L, "os", 17L, 11L, "drift", "请用 drift 模式回答")).willReturn(task);

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

    private CkqaIntegrationProperties buildTaskPolicyProperties() {
        CkqaIntegrationProperties properties = new CkqaIntegrationProperties();
        properties.getPolling().getQueryTaskModeIntervalSeconds().put("drift", 15L);
        properties.getTimeout().getQueryTaskModeStaleSeconds().put("drift", 1800L);
        return properties;
    }
}
