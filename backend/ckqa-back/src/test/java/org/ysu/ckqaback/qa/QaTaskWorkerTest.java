package org.ysu.ckqaback.qa;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.ysu.ckqaback.entity.QaMessages;
import org.ysu.ckqaback.entity.QaRetrievalLogs;
import org.ysu.ckqaback.integration.graphrag.GraphRagTaskClient;
import org.ysu.ckqaback.integration.graphrag.GraphRagTaskCreateResult;
import org.ysu.ckqaback.integration.graphrag.GraphRagTaskSnapshot;
import org.ysu.ckqaback.service.QaMessagesService;
import org.ysu.ckqaback.service.QaRetrievalLogsService;
import org.ysu.ckqaback.service.QaSessionsService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class QaTaskWorkerTest {

    @Test
    void shouldPersistAssistantMessageWhenPythonTaskSucceeds() {
        GraphRagTaskClient taskClient = mock(GraphRagTaskClient.class);
        QaRetrievalLogsService retrievalLogsService = mock(QaRetrievalLogsService.class);
        QaMessagesService messagesService = mock(QaMessagesService.class);
        QaSessionsService sessionsService = mock(QaSessionsService.class);
        TaskExecutor taskExecutor = Runnable::run;

        QaTaskWorker worker = new QaTaskWorker(
                taskExecutor,
                taskClient,
                retrievalLogsService,
                messagesService,
                sessionsService,
                Duration.ofSeconds(5),
                Duration.ofSeconds(30)
        );

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9001L);
        task.setSessionId(5L);
        task.setQueryMode("global");
        task.setQueryText("请概括这套图谱的主题");
        task.setUserMessageId(101L);

        given(retrievalLogsService.getRequiredTask(5L, 9001L)).willReturn(task);
        given(taskClient.createTask("global", "请概括这套图谱的主题"))
                .willReturn(new GraphRagTaskCreateResult("qt_20260422_001", "pending", "queued", LocalDateTime.now()));
        given(taskClient.getTask("qt_20260422_001"))
                .willReturn(Optional.of(new GraphRagTaskSnapshot(
                        "qt_20260422_001",
                        "success",
                        "done",
                        false,
                        LocalDateTime.now(),
                        List.of("done"),
                        "图谱主题集中在操作系统概念网络",
                        null,
                        0,
                        LocalDateTime.now(),
                        LocalDateTime.now()
                )));

        QaMessages assistant = new QaMessages();
        assistant.setId(102L);
        given(messagesService.appendAssistantMessage(5L, "图谱主题集中在操作系统概念网络")).willReturn(assistant);

        worker.processTask(5L, 9001L);

        then(retrievalLogsService).should().bindPythonTask(9001L, "qt_20260422_001", "pending", "queued");
        then(messagesService).should().appendAssistantMessage(5L, "图谱主题集中在操作系统概念网络");
        then(retrievalLogsService).should().markSuccess(anyLong(), anyLong(), contains("done"), contains("success"));
    }

    @Test
    void shouldFailTaskWhenPythonSnapshotDisappearsAfterDispatch() {
        GraphRagTaskClient taskClient = mock(GraphRagTaskClient.class);
        QaRetrievalLogsService retrievalLogsService = mock(QaRetrievalLogsService.class);
        QaMessagesService messagesService = mock(QaMessagesService.class);
        QaSessionsService sessionsService = mock(QaSessionsService.class);
        TaskExecutor taskExecutor = Runnable::run;

        QaTaskWorker worker = new QaTaskWorker(
                taskExecutor,
                taskClient,
                retrievalLogsService,
                messagesService,
                sessionsService,
                Duration.ofSeconds(5),
                Duration.ofSeconds(30)
        );

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9001L);
        task.setSessionId(5L);
        task.setQueryMode("global");
        task.setQueryText("请概括这套图谱的主题");

        given(retrievalLogsService.getRequiredTask(5L, 9001L)).willReturn(task);
        given(taskClient.createTask("global", "请概括这套图谱的主题"))
                .willReturn(new GraphRagTaskCreateResult("qt_20260422_001", "pending", "queued", LocalDateTime.now()));
        given(taskClient.getTask("qt_20260422_001")).willReturn(Optional.empty());

        worker.processTask(5L, 9001L);

        then(retrievalLogsService).should().markFailed(9001L, "failed", "Python 任务快照丢失或服务已重启", "");
    }

    @Test
    void shouldMarkTaskStaleWhenHeartbeatTimeoutExceeded() {
        GraphRagTaskClient taskClient = mock(GraphRagTaskClient.class);
        QaRetrievalLogsService retrievalLogsService = mock(QaRetrievalLogsService.class);
        QaMessagesService messagesService = mock(QaMessagesService.class);
        QaSessionsService sessionsService = mock(QaSessionsService.class);
        TaskExecutor taskExecutor = Runnable::run;

        QaTaskWorker worker = new QaTaskWorker(
                taskExecutor,
                taskClient,
                retrievalLogsService,
                messagesService,
                sessionsService,
                Duration.ofSeconds(5),
                Duration.ofSeconds(30)
        );

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9001L);
        task.setSessionId(5L);
        task.setQueryMode("global");
        task.setQueryText("请概括这套图谱的主题");

        given(retrievalLogsService.getRequiredTask(5L, 9001L)).willReturn(task);
        given(taskClient.createTask("global", "请概括这套图谱的主题"))
                .willReturn(new GraphRagTaskCreateResult("qt_20260422_001", "pending", "queued", LocalDateTime.now()));
        given(taskClient.getTask("qt_20260422_001"))
                .willReturn(Optional.of(new GraphRagTaskSnapshot(
                        "qt_20260422_001",
                        "running",
                        "running",
                        true,
                        LocalDateTime.now().minusSeconds(31),
                        List.of("process alive"),
                        null,
                        null,
                        null,
                        LocalDateTime.now().minusMinutes(1),
                        null
                )));

        worker.processTask(5L, 9001L);

        then(retrievalLogsService).should().markFailed(9001L, "stale", "任务心跳超时", "process alive");
    }
}
