package org.ysu.ckqaback.qa.stream;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.ysu.ckqaback.entity.QaRetrievalLogs;
import org.ysu.ckqaback.integration.graphrag.GraphRagTaskClient;
import org.ysu.ckqaback.integration.graphrag.GraphRagTaskEvent;
import org.ysu.ckqaback.qa.QaWorkflowService;
import org.ysu.ckqaback.qa.dto.QaMessageResponse;
import org.ysu.ckqaback.qa.dto.QaTaskDetailResponse;
import org.ysu.ckqaback.service.QaRetrievalLogsService;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

class QaTaskEventStreamServiceTest {

    @Test
    void shouldScheduleStatusPushAndCancelWhenSuccessIsReached() {
        QaWorkflowService workflowService = mock(QaWorkflowService.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
        AtomicReference<Runnable> scheduledTask = new AtomicReference<>();
        QaTaskStreamProperties properties = new QaTaskStreamProperties();
        properties.setStatusIntervalSeconds(2L);
        properties.setHeartbeatSeconds(15L);
        properties.setDeltaChars(20);

        given(scheduler.scheduleAtFixedRate(any(Runnable.class), eq(0L), eq(2L), eq(TimeUnit.SECONDS)))
                .willAnswer(invocation -> {
                    scheduledTask.set(invocation.getArgument(0));
                    return scheduledFuture;
                });
        given(workflowService.getTaskDetail(5L, 9001L, 7L))
                .willReturn(runningDetail())
                .willReturn(successDetail());

        QaTaskEventStreamService service = new QaTaskEventStreamService(
                workflowService,
                mock(QaRetrievalLogsService.class),
                mock(GraphRagTaskClient.class),
                properties,
                scheduler,
                new SyncTaskExecutor()
        );
        service.openStream(5L, 9001L, 7L);

        assertThat(scheduledTask.get()).isNotNull();
        scheduledTask.get().run();
        scheduledTask.get().run();

        then(workflowService).should(times(2)).getTaskDetail(5L, 9001L, 7L);
        then(scheduledFuture).should().cancel(false);
    }

    @Test
    void shouldRejectWhenStreamIsDisabled() {
        QaTaskStreamProperties properties = new QaTaskStreamProperties();
        properties.setEnabled(false);
        QaTaskEventStreamService service = new QaTaskEventStreamService(
                mock(QaWorkflowService.class),
                mock(QaRetrievalLogsService.class),
                mock(GraphRagTaskClient.class),
                properties,
                mock(ScheduledExecutorService.class),
                new SyncTaskExecutor()
        );

        assertThatThrownBy(() -> service.openStream(5L, 9001L, 7L))
                .hasMessageContaining("QA 任务事件流未启用");
    }

    @Test
    void shouldSubscribePythonTaskEventsWhenPythonTaskIsBound() {
        QaWorkflowService workflowService = mock(QaWorkflowService.class);
        QaRetrievalLogsService retrievalLogsService = mock(QaRetrievalLogsService.class);
        GraphRagTaskClient graphRagTaskClient = mock(GraphRagTaskClient.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
        AtomicReference<Runnable> scheduledTask = new AtomicReference<>();
        QaTaskStreamProperties properties = new QaTaskStreamProperties();
        properties.setStatusIntervalSeconds(2L);

        given(scheduler.scheduleAtFixedRate(any(Runnable.class), eq(0L), eq(2L), eq(TimeUnit.SECONDS)))
                .willAnswer(invocation -> {
                    scheduledTask.set(invocation.getArgument(0));
                    return scheduledFuture;
                });
        given(workflowService.getTaskDetail(5L, 9001L, 7L)).willReturn(runningDetail());
        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setPythonTaskId("qt_stream_1");
        given(retrievalLogsService.getRequiredTask(5L, 9001L)).willReturn(task);
        ObjectMapper objectMapper = new ObjectMapper();
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<GraphRagTaskEvent> consumer = invocation.getArgument(2);
            consumer.accept(new GraphRagTaskEvent(
                    "progress",
                    objectMapper.readTree("""
                            {
                              "type": "context_selected",
                              "mode": "basic",
                              "summary": "已选取 1 个课程片段作为回答依据。",
                              "metrics": {"textUnitCount": 1},
                              "evidence": [{"kind": "text_unit", "title": "操作系统教材"}],
                              "eventSeq": 12
                            }
                            """),
                    12L
            ));
            consumer.accept(new GraphRagTaskEvent(
                    "delta",
                    objectMapper.readTree("{\"text\":\"死锁\",\"eventSeq\":13}"),
                    13L
            ));
            return null;
        }).when(graphRagTaskClient).streamTaskEvents(eq("qt_stream_1"), eq(12L), any());

        TestableQaTaskEventStreamService service = new TestableQaTaskEventStreamService(
                workflowService,
                retrievalLogsService,
                graphRagTaskClient,
                properties,
                scheduler,
                new SyncTaskExecutor()
        );
        service.openStream(5L, 9001L, 7L, 12L);
        scheduledTask.get().run();

        then(graphRagTaskClient).should().streamTaskEvents(eq("qt_stream_1"), eq(12L), any());
        assertThat(service.emitter.renderedEvents).anySatisfy(event -> {
            assertThat(event).contains("id:12");
            assertThat(event).contains("event:progress");
            assertThat(event).contains("已选取 1 个课程片段");
        });
        assertThat(service.emitter.renderedEvents).anySatisfy(event -> {
            assertThat(event).contains("id:13");
            assertThat(event).contains("event:delta");
            assertThat(event).contains("死锁");
        });
    }

    @Test
    void shouldNotReplayFallbackDeltasWhenResumingAfterPythonStreamSeq() {
        QaWorkflowService workflowService = mock(QaWorkflowService.class);
        QaRetrievalLogsService retrievalLogsService = mock(QaRetrievalLogsService.class);
        GraphRagTaskClient graphRagTaskClient = mock(GraphRagTaskClient.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
        AtomicReference<Runnable> scheduledTask = new AtomicReference<>();
        QaTaskStreamProperties properties = new QaTaskStreamProperties();
        properties.setStatusIntervalSeconds(2L);
        properties.setDeltaChars(20);

        given(scheduler.scheduleAtFixedRate(any(Runnable.class), eq(0L), eq(2L), eq(TimeUnit.SECONDS)))
                .willAnswer(invocation -> {
                    scheduledTask.set(invocation.getArgument(0));
                    return scheduledFuture;
                });
        given(workflowService.getTaskDetail(5L, 9001L, 7L)).willReturn(successDetail());
        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setPythonTaskId("qt_stream_1");
        given(retrievalLogsService.getRequiredTask(5L, 9001L)).willReturn(task);
        doAnswer(invocation -> null)
                .when(graphRagTaskClient).streamTaskEvents(eq("qt_stream_1"), eq(25L), any());

        TestableQaTaskEventStreamService service = new TestableQaTaskEventStreamService(
                workflowService,
                retrievalLogsService,
                graphRagTaskClient,
                properties,
                scheduler,
                new SyncTaskExecutor()
        );
        service.openStream(5L, 9001L, 7L, 25L);
        scheduledTask.get().run();

        assertThat(service.emitter.renderedEvents)
                .noneSatisfy(event -> assertThat(event).contains("event:delta"));
        assertThat(service.emitter.renderedEvents)
                .anySatisfy(event -> assertThat(event).contains("event:message"));
        assertThat(service.emitter.renderedEvents)
                .anySatisfy(event -> assertThat(event).contains("event:done"));
    }

    @Test
    void shouldSendErrorEventAndCompleteWhenStatusPushFails() {
        QaWorkflowService workflowService = mock(QaWorkflowService.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
        AtomicReference<Runnable> scheduledTask = new AtomicReference<>();
        QaTaskStreamProperties properties = new QaTaskStreamProperties();
        properties.setStatusIntervalSeconds(2L);
        given(scheduler.scheduleAtFixedRate(any(Runnable.class), eq(0L), eq(2L), eq(TimeUnit.SECONDS)))
                .willAnswer(invocation -> {
                    scheduledTask.set(invocation.getArgument(0));
                    return scheduledFuture;
                });
        given(workflowService.getTaskDetail(5L, 9001L, 7L)).willThrow(new IllegalStateException("boom"));

        TestableQaTaskEventStreamService service = new TestableQaTaskEventStreamService(
                workflowService,
                mock(QaRetrievalLogsService.class),
                mock(GraphRagTaskClient.class),
                properties,
                scheduler,
                new SyncTaskExecutor()
        );
        service.openStream(5L, 9001L, 7L);

        assertThatCode(() -> scheduledTask.get().run()).doesNotThrowAnyException();
        assertThat(service.emitter.events).hasSize(2);
        assertThat(service.emitter.completed).isTrue();
        assertThat(service.emitter.completedWithError).isFalse();
        then(scheduledFuture).should().cancel(false);
    }

    @Test
    void statusEventShouldExposeLatestLogsForProgressDisplay() {
        QaTaskStreamStatusEvent event = QaTaskStreamStatusEvent.from(runningDetailWithLogs());

        assertThat(event.latestLogs()).containsExactly(
                "started native streaming query task provider=native_graphrag",
                "streamed chunk count=3"
        );
    }

    private static QaTaskDetailResponse runningDetail() {
        return QaTaskDetailResponse.of(
                9001L,
                101L,
                null,
                "running",
                "running",
                "running",
                "basic",
                "什么是死锁？",
                List.of(),
                LocalDateTime.of(2026, 5, 20, 10, 0),
                LocalDateTime.of(2026, 5, 20, 10, 0, 5),
                null,
                null,
                null,
                10L,
                300L,
                "任务心跳超时"
        );
    }

    private static QaTaskDetailResponse runningDetailWithLogs() {
        return QaTaskDetailResponse.of(
                9001L,
                101L,
                null,
                "running",
                "streaming",
                "running",
                "global",
                "请总结第一章",
                List.of(
                        "started native streaming query task provider=native_graphrag",
                        "streamed chunk count=3"
                ),
                LocalDateTime.of(2026, 5, 20, 10, 0),
                LocalDateTime.of(2026, 5, 20, 10, 0, 5),
                null,
                null,
                null,
                30L,
                1800L,
                "任务心跳超时"
        );
    }

    private static QaTaskDetailResponse successDetail() {
        return QaTaskDetailResponse.of(
                9001L,
                101L,
                102L,
                "success",
                "done",
                "success",
                "basic",
                "什么是死锁？",
                List.of(),
                LocalDateTime.of(2026, 5, 20, 10, 0),
                LocalDateTime.of(2026, 5, 20, 10, 0, 5),
                LocalDateTime.of(2026, 5, 20, 10, 1),
                QaMessageResponse.of(
                        102L,
                        5L,
                        "assistant",
                        2,
                        "死锁是多个进程互相等待资源而无法继续推进的状态。",
                        LocalDateTime.of(2026, 5, 20, 10, 1),
                        null,
                        null
                ),
                null,
                10L,
                300L,
                "任务心跳超时"
        );
    }

    private static final class TestableQaTaskEventStreamService extends QaTaskEventStreamService {
        private final RecordingSseEmitter emitter = new RecordingSseEmitter();

        private TestableQaTaskEventStreamService(
                QaWorkflowService qaWorkflowService,
                QaRetrievalLogsService qaRetrievalLogsService,
                GraphRagTaskClient graphRagTaskClient,
                QaTaskStreamProperties properties,
                ScheduledExecutorService scheduler,
                SyncTaskExecutor qaTaskExecutor
        ) {
            super(qaWorkflowService, qaRetrievalLogsService, graphRagTaskClient, properties, scheduler, qaTaskExecutor);
        }

        @Override
        protected SseEmitter createEmitter(long timeoutMillis) {
            return emitter;
        }
    }

    private static final class RecordingSseEmitter extends SseEmitter {
        private final List<SseEventBuilder> events = new ArrayList<>();
        private boolean completed;
        private boolean completedWithError;

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            events.add(builder);
            renderedEvents.add(render(builder));
        }

        @Override
        public void complete() {
            completed = true;
        }

        @Override
        public void completeWithError(Throwable ex) {
            completedWithError = true;
        }

        private final List<String> renderedEvents = new ArrayList<>();

        private String render(SseEventBuilder builder) {
            StringBuilder text = new StringBuilder();
            for (ResponseBodyEmitter.DataWithMediaType item : builder.build()) {
                text.append(item.getData());
            }
            return text.toString();
        }
    }
}
