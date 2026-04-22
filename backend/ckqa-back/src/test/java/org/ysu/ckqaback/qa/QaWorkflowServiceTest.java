package org.ysu.ckqaback.qa;

import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.entity.QaMessages;
import org.ysu.ckqaback.entity.QaRetrievalLogs;
import org.ysu.ckqaback.entity.QaSessions;
import org.ysu.ckqaback.qa.dto.CreateQaMessageRequest;
import org.ysu.ckqaback.qa.dto.QaTaskSubmissionResponse;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import org.ysu.ckqaback.service.QaMessagesService;
import org.ysu.ckqaback.service.QaRetrievalLogsService;
import org.ysu.ckqaback.service.QaSessionsService;
import org.ysu.ckqaback.service.UsersService;

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
                qaTaskWorker
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
        given(qaRetrievalLogsService.createPendingTask(5L, "os", 17L, 11L, "global", "请概括这套图谱的主题")).willReturn(task);

        QaTaskSubmissionResponse response = workflowService.sendMessage(5L, new CreateQaMessageRequest("global", "请概括这套图谱的主题"));

        assertThat(response.getTaskId()).isEqualTo(9001L);
        assertThat(response.getTaskStatus()).isEqualTo("pending");
        assertThat(response.getProgressStage()).isEqualTo("queued");
        then(qaTaskWorker).should().dispatch(5L, 9001L);
        then(qaMessagesService).should(never()).appendAssistantMessage(anyLong(), eq("请概括这套图谱的主题"));
    }

    private KnowledgeBases buildKnowledgeBase() {
        KnowledgeBases knowledgeBase = new KnowledgeBases();
        knowledgeBase.setId(3L);
        knowledgeBase.setCourseId("os");
        knowledgeBase.setActiveIndexRunId(17L);
        return knowledgeBase;
    }
}
