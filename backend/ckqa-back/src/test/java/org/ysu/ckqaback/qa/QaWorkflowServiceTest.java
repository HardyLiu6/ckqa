package org.ysu.ckqaback.qa;

import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.entity.QaMessages;
import org.ysu.ckqaback.entity.QaRetrievalLogs;
import org.ysu.ckqaback.entity.QaSessions;
import org.ysu.ckqaback.integration.graphrag.GraphRagQueryClient;
import org.ysu.ckqaback.qa.dto.CreateQaMessageRequest;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import org.ysu.ckqaback.service.QaMessagesService;
import org.ysu.ckqaback.service.QaRetrievalLogsService;
import org.ysu.ckqaback.service.QaSessionsService;
import org.ysu.ckqaback.service.UsersService;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

class QaWorkflowServiceTest {

    @Test
    void shouldTouchSessionLastMessageAtWhenRetrievalFails() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        UsersService usersService = mock(UsersService.class);
        GraphRagQueryClient graphRagQueryClient = mock(GraphRagQueryClient.class);

        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                usersService,
                graphRagQueryClient
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
        userMessage.setContent("什么是线程");

        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(qaMessagesService.appendUserMessage(5L, "什么是线程")).willReturn(userMessage);
        given(graphRagQueryClient.query("local", "什么是线程")).willThrow(new RuntimeException("upstream 500"));

        assertThatThrownBy(() -> workflowService.sendMessage(5L, new CreateQaMessageRequest("local", "什么是线程")))
                .isInstanceOf(RuntimeException.class);

        then(qaSessionsService).should().touchLastMessageAt(5L);
    }

    @Test
    void shouldPersistFailedRetrievalWithoutAssistantMessage() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        UsersService usersService = mock(UsersService.class);
        GraphRagQueryClient graphRagQueryClient = mock(GraphRagQueryClient.class);

        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                usersService,
                graphRagQueryClient
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
        userMessage.setContent("什么是线程");

        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(qaMessagesService.appendUserMessage(5L, "什么是线程")).willReturn(userMessage);
        given(graphRagQueryClient.query("local", "什么是线程")).willThrow(new RuntimeException("upstream 500"));

        assertThatThrownBy(() -> workflowService.sendMessage(5L, new CreateQaMessageRequest("local", "什么是线程")))
                .isInstanceOf(RuntimeException.class);

        then(qaMessagesService).should().appendUserMessage(5L, "什么是线程");
        then(qaRetrievalLogsService).should().createFailureLog(
                eq(5L),
                eq("os"),
                eq(17L),
                eq("local"),
                eq("什么是线程"),
                contains("upstream 500")
        );
        then(qaSessionsService).should().touchLastMessageAt(5L);
        then(qaMessagesService).should(never()).appendAssistantMessage(anyLong(), anyString());
    }

    @Test
    void shouldTouchSessionLastMessageAtForBothUserAndAssistantMessagesOnSuccess() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        UsersService usersService = mock(UsersService.class);
        GraphRagQueryClient graphRagQueryClient = mock(GraphRagQueryClient.class);

        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                usersService,
                graphRagQueryClient
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
        userMessage.setContent("什么是线程");

        QaMessages assistantMessage = new QaMessages();
        assistantMessage.setId(12L);
        assistantMessage.setSessionId(5L);
        assistantMessage.setRole("assistant");
        assistantMessage.setSequenceNo(2);
        assistantMessage.setContent("线程是调度的基本单位");

        QaRetrievalLogs retrievalLog = new QaRetrievalLogs();
        retrievalLog.setId(20L);
        retrievalLog.setRetrievalStatus("success");

        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(qaMessagesService.appendUserMessage(5L, "什么是线程")).willReturn(userMessage);
        given(graphRagQueryClient.query("local", "什么是线程"))
                .willReturn(new org.ysu.ckqaback.integration.graphrag.GraphRagChatResult("线程是调度的基本单位"));
        given(qaRetrievalLogsService.createSuccessLog(5L, "os", 17L, "local", "什么是线程")).willReturn(retrievalLog);
        given(qaMessagesService.appendAssistantMessage(5L, "线程是调度的基本单位")).willReturn(assistantMessage);

        workflowService.sendMessage(5L, new CreateQaMessageRequest("local", "什么是线程"));

        then(qaSessionsService).should(times(2)).touchLastMessageAt(5L);
    }

    private KnowledgeBases buildKnowledgeBase() {
        KnowledgeBases knowledgeBase = new KnowledgeBases();
        knowledgeBase.setId(3L);
        knowledgeBase.setCourseId("os");
        knowledgeBase.setActiveIndexRunId(17L);
        return knowledgeBase;
    }
}
