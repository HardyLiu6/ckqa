package org.ysu.ckqaback.qa;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.ysu.ckqaback.entity.IndexArtifacts;
import org.ysu.ckqaback.entity.QaMessages;
import org.ysu.ckqaback.entity.QaRetrievalLogs;
import org.ysu.ckqaback.integration.graphrag.GraphRagTaskClient;
import org.ysu.ckqaback.integration.graphrag.GraphRagTaskCreateResult;
import org.ysu.ckqaback.integration.graphrag.GraphRagConversationMessage;
import org.ysu.ckqaback.integration.graphrag.GraphRagSourceSnapshot;
import org.ysu.ckqaback.integration.graphrag.GraphRagTaskSnapshot;
import org.ysu.ckqaback.qa.memory.QaLearningMemoryCaptureService;
import org.ysu.ckqaback.qa.summary.QaSessionSummaryService;
import org.ysu.ckqaback.service.IndexArtifactsService;
import org.ysu.ckqaback.service.QaMessagesService;
import org.ysu.ckqaback.service.QaRetrievalHitsService;
import org.ysu.ckqaback.service.QaRetrievalLogsService;
import org.ysu.ckqaback.service.QaSessionsService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QaTaskWorkerTest {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    void shouldMarkTaskFailedWhenTaskLookupFailsBeforeDispatchingPythonTask() {
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
                Duration.ofSeconds(30),
                Clock.fixed(Instant.parse("2026-05-05T12:00:40Z"), SHANGHAI_ZONE)
        );

        given(retrievalLogsService.getRequiredTask(5L, 9001L)).willThrow(new RuntimeException("问答任务不存在"));

        assertThrows(RuntimeException.class, () -> worker.processTask(5L, 9001L));

        then(taskClient).should(never()).createTask("basic", "问题", null, null, null);
        then(retrievalLogsService).should().markFailed(9001L, "failed", "问答任务不存在", "");
    }

    @Test
    void shouldSubmitPythonTaskWithReadyOutputArtifactContext() {
        GraphRagTaskClient taskClient = mock(GraphRagTaskClient.class);
        QaRetrievalLogsService retrievalLogsService = mock(QaRetrievalLogsService.class);
        QaMessagesService messagesService = mock(QaMessagesService.class);
        QaSessionsService sessionsService = mock(QaSessionsService.class);
        IndexArtifactsService artifactsService = mock(IndexArtifactsService.class);
        TaskExecutor taskExecutor = Runnable::run;

        QaTaskWorker worker = new QaTaskWorker(
                taskExecutor,
                taskClient,
                retrievalLogsService,
                messagesService,
                sessionsService,
                artifactsService,
                mode -> Duration.ZERO,
                mode -> Duration.ofSeconds(30),
                mode -> "任务心跳超时",
                Clock.fixed(Instant.parse("2026-05-05T12:00:40Z"), SHANGHAI_ZONE)
        );

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9001L);
        task.setSessionId(5L);
        task.setIndexRunId(18L);
        task.setQueryMode("basic");
        task.setQueryText("问题");

        IndexArtifacts outputArtifact = new IndexArtifacts();
        outputArtifact.setIndexRunId(18L);
        outputArtifact.setArtifactType("output_dir");
        outputArtifact.setArtifactStatus("ready");
        outputArtifact.setStorageUri("user_2/kb_5/build_27/index/output");

        given(retrievalLogsService.getRequiredTask(5L, 9001L)).willReturn(task);
        given(artifactsService.listByIndexRunId(18L)).willReturn(List.of(outputArtifact));
        given(taskClient.createTask("basic", "问题", 18L, "user_2/kb_5/build_27/index/output", null))
                .willReturn(new GraphRagTaskCreateResult("qt_20260505_001", "pending", "queued", LocalDateTime.now()));
        given(taskClient.getTask("qt_20260505_001"))
                .willReturn(Optional.of(new GraphRagTaskSnapshot(
                        "qt_20260505_001",
                        "success",
                        "done",
                        false,
                        LocalDateTime.now(),
                        List.of("done"),
                        "回答",
                        null,
                        0,
                        LocalDateTime.now(),
                        LocalDateTime.now()
                )));

        QaMessages assistant = new QaMessages();
        assistant.setId(102L);
        given(messagesService.appendAssistantMessage(5L, "回答")).willReturn(assistant);

        worker.processTask(5L, 9001L);

        then(taskClient).should().createTask("basic", "问题", 18L, "user_2/kb_5/build_27/index/output", null);
        then(taskClient).should(never()).createTask("basic", "问题", null, null, null);
        then(retrievalLogsService).should().markSuccess(9001L, 102L, "done", "success");
    }

    @Test
    void shouldNormalizeBusinessMemoryStrategyBeforeSubmittingLocalHistoryTask() {
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
                Duration.ofSeconds(30),
                Clock.fixed(Instant.parse("2026-05-20T12:00:40Z"), SHANGHAI_ZONE)
        );

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9002L);
        task.setSessionId(5L);
        task.setQueryMode("local");
        task.setQueryText("关于上一轮主题「时间片轮转」：它为什么影响响应时间？");
        task.setQueryEngineStrategy("local_history_preference_only");
        task.setMemoryHistoryJson("""
                [
                  {"role": "user", "content": "什么是时间片轮转？"},
                  {"role": "assistant", "content": "时间片轮转是一种抢占式调度算法。"}
                ]
                """);

        given(retrievalLogsService.getRequiredTask(5L, 9002L)).willReturn(task);
        given(taskClient.createTask(
                "local",
                "关于上一轮主题「时间片轮转」：它为什么影响响应时间？",
                null,
                null,
                null,
                "local_history",
                List.of(
                        new GraphRagConversationMessage("user", "什么是时间片轮转？"),
                        new GraphRagConversationMessage("assistant", "时间片轮转是一种抢占式调度算法。")
                ),
                false
        )).willReturn(new GraphRagTaskCreateResult("qt_20260520_002", "pending", "queued", LocalDateTime.now()));
        given(taskClient.getTask("qt_20260520_002"))
                .willReturn(Optional.of(new GraphRagTaskSnapshot(
                        "qt_20260520_002",
                        "success",
                        "done",
                        false,
                        LocalDateTime.now(),
                        List.of("done"),
                        "时间片越短，响应越快但切换开销更高。",
                        null,
                        0,
                        LocalDateTime.now(),
                        LocalDateTime.now()
                )));

        QaMessages assistant = new QaMessages();
        assistant.setId(202L);
        given(messagesService.appendAssistantMessage(5L, "时间片越短，响应越快但切换开销更高。")).willReturn(assistant);

        worker.processTask(5L, 9002L);

        then(taskClient).should().createTask(
                "local",
                "关于上一轮主题「时间片轮转」：它为什么影响响应时间？",
                null,
                null,
                null,
                "local_history",
                List.of(
                        new GraphRagConversationMessage("user", "什么是时间片轮转？"),
                        new GraphRagConversationMessage("assistant", "时间片轮转是一种抢占式调度算法。")
                ),
                false
        );
        then(retrievalLogsService).should().markSuccess(9002L, 202L, "done", "success");
    }

    @Test
    void shouldFailTaskWhenReadyOutputArtifactIsMissing() {
        GraphRagTaskClient taskClient = mock(GraphRagTaskClient.class);
        QaRetrievalLogsService retrievalLogsService = mock(QaRetrievalLogsService.class);
        QaMessagesService messagesService = mock(QaMessagesService.class);
        QaSessionsService sessionsService = mock(QaSessionsService.class);
        IndexArtifactsService artifactsService = mock(IndexArtifactsService.class);
        TaskExecutor taskExecutor = Runnable::run;

        QaTaskWorker worker = new QaTaskWorker(
                taskExecutor,
                taskClient,
                retrievalLogsService,
                messagesService,
                sessionsService,
                artifactsService,
                mode -> Duration.ZERO,
                mode -> Duration.ofSeconds(30),
                mode -> "任务心跳超时",
                Clock.fixed(Instant.parse("2026-05-05T12:00:40Z"), SHANGHAI_ZONE)
        );

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9001L);
        task.setSessionId(5L);
        task.setIndexRunId(18L);
        task.setQueryMode("basic");
        task.setQueryText("问题");

        given(retrievalLogsService.getRequiredTask(5L, 9001L)).willReturn(task);
        given(artifactsService.listByIndexRunId(18L)).willReturn(List.of());

        try {
            worker.processTask(5L, 9001L);
        } catch (RuntimeException ignored) {
            // worker 会记录失败并向调用方保留原始业务异常。
        }

        then(taskClient).should(never()).createTask("basic", "问题", null, null, null);
        then(retrievalLogsService).should().markFailed(9001L, "failed", "知识库当前没有可用索引", "");
    }

    @Test
    void shouldPersistAssistantMessageWhenPythonTaskSucceeds() {
        GraphRagTaskClient taskClient = mock(GraphRagTaskClient.class);
        QaRetrievalLogsService retrievalLogsService = mock(QaRetrievalLogsService.class);
        QaRetrievalHitsService retrievalHitsService = mock(QaRetrievalHitsService.class);
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
                Duration.ofSeconds(30),
                Clock.fixed(Instant.parse("2026-04-22T12:00:40Z"), SHANGHAI_ZONE)
        );
        worker.setQaRetrievalHitsService(retrievalHitsService);

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9001L);
        task.setSessionId(5L);
        task.setQueryMode("global");
        task.setQueryText("请概括这套图谱的主题");
        task.setUserMessageId(101L);

        given(retrievalLogsService.getRequiredTask(5L, 9001L)).willReturn(task);
        given(taskClient.createTask("global", "请概括这套图谱的主题", null, null, null))
                .willReturn(new GraphRagTaskCreateResult("qt_20260422_001", "pending", "queued", LocalDateTime.now()));
        GraphRagSourceSnapshot source = new GraphRagSourceSnapshot(
                1,
                "source",
                "156",
                "chunk-1",
                "doc-1",
                "操作系统教材",
                "第3章/死锁",
                123,
                124,
                "死锁相关来源片段"
        );
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
                        LocalDateTime.now(),
                        List.of(source)
                )));

        QaMessages assistant = new QaMessages();
        assistant.setId(102L);
        given(messagesService.appendAssistantMessage(5L, "图谱主题集中在操作系统概念网络")).willReturn(assistant);

        worker.processTask(5L, 9001L);

        then(retrievalLogsService).should().bindPythonTask(9001L, "qt_20260422_001", "pending", "queued");
        then(messagesService).should().appendAssistantMessage(5L, "图谱主题集中在操作系统概念网络");
        then(retrievalHitsService).should().replaceHits(9001L, List.of(source));
        then(retrievalLogsService).should().markSuccess(anyLong(), anyLong(), contains("done"), contains("success"));
    }

    @Test
    void shouldRequestNativeStreamingWhenStreamModeEnabledWithoutBuildRunArtifact() {
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
                null,
                mode -> Duration.ZERO,
                mode -> Duration.ofSeconds(30),
                mode -> "任务心跳超时",
                mode -> true,
                Clock.fixed(Instant.parse("2026-04-22T12:00:40Z"), SHANGHAI_ZONE)
        );

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9001L);
        task.setSessionId(5L);
        task.setQueryMode("global");
        task.setQueryText("请概括这套图谱的主题");
        task.setUserMessageId(101L);

        given(retrievalLogsService.getRequiredTask(5L, 9001L)).willReturn(task);
        given(taskClient.createTask("global", "请概括这套图谱的主题", null, null, null))
                .willReturn(new GraphRagTaskCreateResult("qt_20260422_001", "pending", "queued", LocalDateTime.now()));
        given(taskClient.createTask("global", "请概括这套图谱的主题", null, null, null, null, null, true))
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

        then(taskClient).should().createTask("global", "请概括这套图谱的主题", null, null, null, null, null, true);
        then(taskClient).should(never()).createTask("global", "请概括这套图谱的主题", null, null, null);
    }

    @Test
    void shouldKeepAssistantMessageAndSuccessWhenSourcePersistenceFails() {
        GraphRagTaskClient taskClient = mock(GraphRagTaskClient.class);
        QaRetrievalLogsService retrievalLogsService = mock(QaRetrievalLogsService.class);
        QaRetrievalHitsService retrievalHitsService = mock(QaRetrievalHitsService.class);
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
                Duration.ofSeconds(30),
                Clock.fixed(Instant.parse("2026-04-22T12:00:40Z"), SHANGHAI_ZONE)
        );
        worker.setQaRetrievalHitsService(retrievalHitsService);

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9001L);
        task.setSessionId(5L);
        task.setQueryMode("hybrid_v0");
        task.setQueryText("死锁和资源分配图有什么关系？");

        GraphRagSourceSnapshot source = new GraphRagSourceSnapshot(
                1,
                "bm25",
                "bm25",
                "tu-bm25-001",
                "tu-bm25-001",
                "tu-bm25-001",
                "操作系统教材",
                "",
                null,
                null,
                "资源分配图片段"
        );
        given(retrievalLogsService.getRequiredTask(5L, 9001L)).willReturn(task);
        given(taskClient.createTask("hybrid_v0", "死锁和资源分配图有什么关系？", null, null, null))
                .willReturn(new GraphRagTaskCreateResult("qt_20260518_001", "pending", "queued", LocalDateTime.now()));
        given(taskClient.getTask("qt_20260518_001"))
                .willReturn(Optional.of(new GraphRagTaskSnapshot(
                        "qt_20260518_001",
                        "success",
                        "done",
                        false,
                        LocalDateTime.now(),
                        List.of("done"),
                        "死锁和资源分配图有关。",
                        null,
                        0,
                        LocalDateTime.now(),
                        LocalDateTime.now(),
                        List.of(source)
                )));

        QaMessages assistant = new QaMessages();
        assistant.setId(102L);
        given(messagesService.appendAssistantMessage(5L, "死锁和资源分配图有关。")).willReturn(assistant);
        doThrow(new RuntimeException("hits down")).when(retrievalHitsService).replaceHits(9001L, List.of(source));

        worker.processTask(5L, 9001L);

        then(messagesService).should().appendAssistantMessage(5L, "死锁和资源分配图有关。");
        then(retrievalLogsService).should().markSuccess(9001L, 102L, "done", "success");
    }

    @Test
    void shouldTriggerSummaryRefreshAfterAssistantMessageSucceeds() {
        GraphRagTaskClient taskClient = mock(GraphRagTaskClient.class);
        QaRetrievalLogsService retrievalLogsService = mock(QaRetrievalLogsService.class);
        QaMessagesService messagesService = mock(QaMessagesService.class);
        QaSessionsService sessionsService = mock(QaSessionsService.class);
        QaSessionSummaryService summaryService = mock(QaSessionSummaryService.class);
        TaskExecutor taskExecutor = Runnable::run;

        QaTaskWorker worker = new QaTaskWorker(
                taskExecutor,
                taskClient,
                retrievalLogsService,
                messagesService,
                sessionsService,
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                Clock.fixed(Instant.parse("2026-04-22T12:00:40Z"), SHANGHAI_ZONE)
        );
        worker.setQaSessionSummaryService(summaryService);

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9001L);
        task.setSessionId(5L);
        task.setQueryMode("basic");
        task.setQueryText("什么是死锁？");

        given(retrievalLogsService.getRequiredTask(5L, 9001L)).willReturn(task);
        given(taskClient.createTask("basic", "什么是死锁？", null, null, null))
                .willReturn(new GraphRagTaskCreateResult("qt_20260517_001", "pending", "queued", LocalDateTime.now()));
        given(taskClient.getTask("qt_20260517_001"))
                .willReturn(Optional.of(new GraphRagTaskSnapshot(
                        "qt_20260517_001",
                        "success",
                        "done",
                        false,
                        LocalDateTime.now(),
                        List.of("done"),
                        "死锁是多个进程互相等待资源。",
                        null,
                        0,
                        LocalDateTime.now(),
                        LocalDateTime.now()
                )));

        QaMessages assistant = new QaMessages();
        assistant.setId(102L);
        given(messagesService.appendAssistantMessage(5L, "死锁是多个进程互相等待资源。")).willReturn(assistant);

        worker.processTask(5L, 9001L);

        then(summaryService).should().checkAndSummarizeAsync(5L);
    }

    @Test
    void shouldKeepTaskSuccessWhenSummaryRefreshFails() {
        GraphRagTaskClient taskClient = mock(GraphRagTaskClient.class);
        QaRetrievalLogsService retrievalLogsService = mock(QaRetrievalLogsService.class);
        QaMessagesService messagesService = mock(QaMessagesService.class);
        QaSessionsService sessionsService = mock(QaSessionsService.class);
        QaSessionSummaryService summaryService = mock(QaSessionSummaryService.class);
        TaskExecutor taskExecutor = Runnable::run;

        QaTaskWorker worker = new QaTaskWorker(
                taskExecutor,
                taskClient,
                retrievalLogsService,
                messagesService,
                sessionsService,
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                Clock.fixed(Instant.parse("2026-04-22T12:00:40Z"), SHANGHAI_ZONE)
        );
        worker.setQaSessionSummaryService(summaryService);

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9001L);
        task.setSessionId(5L);
        task.setQueryMode("basic");
        task.setQueryText("什么是死锁？");

        given(retrievalLogsService.getRequiredTask(5L, 9001L)).willReturn(task);
        given(taskClient.createTask("basic", "什么是死锁？", null, null, null))
                .willReturn(new GraphRagTaskCreateResult("qt_20260517_001", "pending", "queued", LocalDateTime.now()));
        given(taskClient.getTask("qt_20260517_001"))
                .willReturn(Optional.of(new GraphRagTaskSnapshot(
                        "qt_20260517_001",
                        "success",
                        "done",
                        false,
                        LocalDateTime.now(),
                        List.of("done"),
                        "死锁是多个进程互相等待资源。",
                        null,
                        0,
                        LocalDateTime.now(),
                        LocalDateTime.now()
                )));

        QaMessages assistant = new QaMessages();
        assistant.setId(102L);
        given(messagesService.appendAssistantMessage(5L, "死锁是多个进程互相等待资源。")).willReturn(assistant);
        doThrow(new RuntimeException("summary down")).when(summaryService).checkAndSummarizeAsync(5L);

        worker.processTask(5L, 9001L);

        then(retrievalLogsService).should().markSuccess(9001L, 102L, "done", "success");
    }

    @Test
    void shouldTriggerLearningMemoryCaptureAfterAssistantMessageSucceeds() {
        GraphRagTaskClient taskClient = mock(GraphRagTaskClient.class);
        QaRetrievalLogsService retrievalLogsService = mock(QaRetrievalLogsService.class);
        QaMessagesService messagesService = mock(QaMessagesService.class);
        QaSessionsService sessionsService = mock(QaSessionsService.class);
        QaLearningMemoryCaptureService captureService = mock(QaLearningMemoryCaptureService.class);
        TaskExecutor taskExecutor = Runnable::run;

        QaTaskWorker worker = new QaTaskWorker(
                taskExecutor,
                taskClient,
                retrievalLogsService,
                messagesService,
                sessionsService,
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                Clock.fixed(Instant.parse("2026-04-22T12:00:40Z"), SHANGHAI_ZONE)
        );
        worker.setQaLearningMemoryCaptureService(captureService);

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9001L);
        task.setSessionId(5L);
        task.setQueryMode("local");
        task.setQueryText("什么是死锁？请用步骤解释");

        given(retrievalLogsService.getRequiredTask(5L, 9001L)).willReturn(task);
        given(taskClient.createTask("local", "什么是死锁？请用步骤解释", null, null, null))
                .willReturn(new GraphRagTaskCreateResult("qt_20260520_001", "pending", "queued", LocalDateTime.now()));
        given(taskClient.getTask("qt_20260520_001"))
                .willReturn(Optional.of(new GraphRagTaskSnapshot(
                        "qt_20260520_001",
                        "success",
                        "done",
                        false,
                        LocalDateTime.now(),
                        List.of("done"),
                        "死锁是多个进程相互等待资源。",
                        null,
                        0,
                        LocalDateTime.now(),
                        LocalDateTime.now()
                )));

        QaMessages assistant = new QaMessages();
        assistant.setId(102L);
        given(messagesService.appendAssistantMessage(5L, "死锁是多个进程相互等待资源。")).willReturn(assistant);

        worker.processTask(5L, 9001L);

        then(retrievalLogsService).should().markSuccess(9001L, 102L, "done", "success");
        then(captureService).should().captureAfterAssistantSuccess(task, assistant);
    }

    @Test
    void shouldKeepTaskSuccessWhenLearningMemoryCaptureFails() {
        GraphRagTaskClient taskClient = mock(GraphRagTaskClient.class);
        QaRetrievalLogsService retrievalLogsService = mock(QaRetrievalLogsService.class);
        QaMessagesService messagesService = mock(QaMessagesService.class);
        QaSessionsService sessionsService = mock(QaSessionsService.class);
        QaLearningMemoryCaptureService captureService = mock(QaLearningMemoryCaptureService.class);
        TaskExecutor taskExecutor = Runnable::run;

        QaTaskWorker worker = new QaTaskWorker(
                taskExecutor,
                taskClient,
                retrievalLogsService,
                messagesService,
                sessionsService,
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                Clock.fixed(Instant.parse("2026-04-22T12:00:40Z"), SHANGHAI_ZONE)
        );
        worker.setQaLearningMemoryCaptureService(captureService);

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9001L);
        task.setSessionId(5L);
        task.setQueryMode("local");
        task.setQueryText("什么是死锁？");

        given(retrievalLogsService.getRequiredTask(5L, 9001L)).willReturn(task);
        given(taskClient.createTask("local", "什么是死锁？", null, null, null))
                .willReturn(new GraphRagTaskCreateResult("qt_20260520_002", "pending", "queued", LocalDateTime.now()));
        given(taskClient.getTask("qt_20260520_002"))
                .willReturn(Optional.of(new GraphRagTaskSnapshot(
                        "qt_20260520_002",
                        "success",
                        "done",
                        false,
                        LocalDateTime.now(),
                        List.of("done"),
                        "死锁是多个进程相互等待资源。",
                        null,
                        0,
                        LocalDateTime.now(),
                        LocalDateTime.now()
                )));

        QaMessages assistant = new QaMessages();
        assistant.setId(102L);
        given(messagesService.appendAssistantMessage(5L, "死锁是多个进程相互等待资源。")).willReturn(assistant);
        doThrow(new RuntimeException("memory down")).when(captureService).captureAfterAssistantSuccess(task, assistant);

        worker.processTask(5L, 9001L);

        then(captureService).should().captureAfterAssistantSuccess(task, assistant);
        then(retrievalLogsService).should().markSuccess(9001L, 102L, "done", "success");
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
                Duration.ofSeconds(30),
                Clock.fixed(Instant.parse("2026-04-22T12:00:40Z"), SHANGHAI_ZONE)
        );

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9001L);
        task.setSessionId(5L);
        task.setQueryMode("global");
        task.setQueryText("请概括这套图谱的主题");

        given(retrievalLogsService.getRequiredTask(5L, 9001L)).willReturn(task);
        given(taskClient.createTask("global", "请概括这套图谱的主题", null, null, null))
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
                Duration.ofSeconds(30),
                Clock.fixed(Instant.parse("2026-04-22T12:00:31Z"), SHANGHAI_ZONE)
        );

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9001L);
        task.setSessionId(5L);
        task.setQueryMode("global");
        task.setQueryText("请概括这套图谱的主题");

        given(retrievalLogsService.getRequiredTask(5L, 9001L)).willReturn(task);
        given(taskClient.createTask("global", "请概括这套图谱的主题", null, null, null))
                .willReturn(new GraphRagTaskCreateResult("qt_20260422_001", "pending", "queued", LocalDateTime.now()));
        given(taskClient.getTask("qt_20260422_001"))
                .willReturn(Optional.of(new GraphRagTaskSnapshot(
                        "qt_20260422_001",
                        "running",
                        "running",
                        true,
                        LocalDateTime.of(2026, 4, 22, 20, 0, 0),
                        List.of("process alive"),
                        null,
                        null,
                        null,
                        LocalDateTime.of(2026, 4, 22, 19, 59, 0),
                        null
                )));

        worker.processTask(5L, 9001L);

        then(retrievalLogsService).should().markFailed(9001L, "stale", "任务心跳超时", "process alive");
    }

    @Test
    void shouldUseModeSpecificStaleThresholdForDriftTask() {
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
                mode -> Duration.ZERO,
                mode -> "drift".equals(mode) ? Duration.ofSeconds(60) : Duration.ofSeconds(30),
                mode -> "drift".equals(mode) ? "drift 模式心跳超时" : "任务心跳超时",
                Clock.fixed(Instant.parse("2026-04-22T12:00:45Z"), SHANGHAI_ZONE)
        );

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9001L);
        task.setSessionId(5L);
        task.setQueryMode("drift");
        task.setQueryText("请从局部上下文漂移检索课程主题");

        given(retrievalLogsService.getRequiredTask(5L, 9001L)).willReturn(task);
        given(taskClient.createTask("drift", "请从局部上下文漂移检索课程主题", null, null, null))
                .willReturn(new GraphRagTaskCreateResult("qt_20260422_001", "pending", "queued", LocalDateTime.now()));
        given(taskClient.getTask("qt_20260422_001"))
                .willReturn(
                        Optional.of(new GraphRagTaskSnapshot(
                                "qt_20260422_001",
                                "running",
                                "running",
                                true,
                                LocalDateTime.of(2026, 4, 22, 20, 0, 0),
                                List.of("drift still running"),
                                null,
                                null,
                                null,
                                LocalDateTime.of(2026, 4, 22, 19, 59, 59),
                                null
                        )),
                        Optional.of(new GraphRagTaskSnapshot(
                                "qt_20260422_001",
                                "success",
                                "done",
                                false,
                                LocalDateTime.of(2026, 4, 22, 20, 0, 46),
                                List.of("drift done"),
                                "drift 检索结果",
                                null,
                                0,
                                LocalDateTime.of(2026, 4, 22, 19, 59, 59),
                                LocalDateTime.of(2026, 4, 22, 20, 0, 46)
                        ))
                );

        QaMessages assistant = new QaMessages();
        assistant.setId(102L);
        given(messagesService.appendAssistantMessage(5L, "drift 检索结果")).willReturn(assistant);

        worker.processTask(5L, 9001L);

        then(retrievalLogsService).should(never()).markFailed(9001L, "stale", "drift 模式心跳超时", "drift still running");
        then(retrievalLogsService).should().markSuccess(9001L, 102L, "drift done", "success");
    }
}
