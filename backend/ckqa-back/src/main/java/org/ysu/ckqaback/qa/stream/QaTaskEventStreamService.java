package org.ysu.ckqaback.qa.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.QaRetrievalLogs;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.integration.graphrag.GraphRagTaskClient;
import org.ysu.ckqaback.integration.graphrag.GraphRagTaskEvent;
import org.ysu.ckqaback.qa.QaWorkflowService;
import org.ysu.ckqaback.qa.dto.QaMessageResponse;
import org.ysu.ckqaback.qa.dto.QaTaskDetailResponse;
import org.ysu.ckqaback.service.QaRetrievalLogsService;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 学生端 QA 任务 SSE 事件流。
 */
@Slf4j
@Service
public class QaTaskEventStreamService {

    private final QaWorkflowService qaWorkflowService;
    private final QaRetrievalLogsService qaRetrievalLogsService;
    private final GraphRagTaskClient graphRagTaskClient;
    private final QaTaskStreamProperties properties;
    private final ScheduledExecutorService scheduler;
    private final TaskExecutor qaTaskExecutor;

    public QaTaskEventStreamService(
            QaWorkflowService qaWorkflowService,
            QaRetrievalLogsService qaRetrievalLogsService,
            GraphRagTaskClient graphRagTaskClient,
            QaTaskStreamProperties properties,
            @Qualifier("qaTaskEventScheduler") ScheduledExecutorService scheduler,
            @Qualifier("qaTaskExecutor") TaskExecutor qaTaskExecutor
    ) {
        this.qaWorkflowService = qaWorkflowService;
        this.qaRetrievalLogsService = qaRetrievalLogsService;
        this.graphRagTaskClient = graphRagTaskClient;
        this.properties = properties;
        this.scheduler = scheduler;
        this.qaTaskExecutor = qaTaskExecutor;
    }

    public SseEmitter openStream(Long sessionId, Long taskId, Long currentUserId) {
        return openStream(sessionId, taskId, currentUserId, 0L);
    }

    public SseEmitter openStream(Long sessionId, Long taskId, Long currentUserId, Long afterEventSeq) {
        if (!properties.isEnabled()) {
            throw new BusinessException(
                    ApiResultCode.PIPELINE_NOT_IMPLEMENTED,
                    HttpStatus.NOT_FOUND,
                    "QA 任务事件流未启用，前端可回退到轮询"
            );
        }

        SseEmitter emitter = createEmitter(properties.timeoutMillis());
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();
        AtomicReference<String> bridgedPythonTaskId = new AtomicReference<>();
        AtomicBoolean pythonDeltaForwarded = new AtomicBoolean(false);
        AtomicLong lastHeartbeatEpochMillis = new AtomicLong(0L);
        Runnable cleanup = () -> cancelScheduled(futureRef);

        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(error -> cleanup.run());

        try {
            sendEvent(emitter, "ack", Map.of("sessionId", sessionId, "taskId", taskId));
        } catch (IOException ex) {
            log.debug("QA 任务事件流 ack 推送失败, sessionId={}, taskId={}: {}", sessionId, taskId, ex.getMessage());
            completeQuietly(emitter);
            return emitter;
        }

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> pushSnapshot(
                        sessionId,
                        taskId,
                        currentUserId,
                        emitter,
                        futureRef,
                        bridgedPythonTaskId,
                        pythonDeltaForwarded,
                        lastHeartbeatEpochMillis,
                        afterEventSeq == null ? 0L : Math.max(0L, afterEventSeq)
                ),
                0L,
                properties.statusIntervalSeconds(),
                TimeUnit.SECONDS
        );
        futureRef.set(future);
        return emitter;
    }

    protected SseEmitter createEmitter(long timeoutMillis) {
        return new SseEmitter(timeoutMillis);
    }

    private void pushSnapshot(
            Long sessionId,
            Long taskId,
            Long currentUserId,
            SseEmitter emitter,
            AtomicReference<ScheduledFuture<?>> futureRef,
            AtomicReference<String> bridgedPythonTaskId,
            AtomicBoolean pythonDeltaForwarded,
            AtomicLong lastHeartbeatEpochMillis,
            Long afterEventSeq
    ) {
        try {
            QaTaskDetailResponse detail = qaWorkflowService.getTaskDetail(sessionId, taskId, currentUserId);
            sendEvent(emitter, "status", QaTaskStreamStatusEvent.from(detail));
            sendHeartbeatIfDue(taskId, emitter, lastHeartbeatEpochMillis);
            startPythonBridgeIfAvailable(sessionId, taskId, emitter, bridgedPythonTaskId, pythonDeltaForwarded, afterEventSeq);

            if (!isTerminal(detail.getTaskStatus())) {
                return;
            }

            QaTaskDetailResponse terminalDetail = resolveTerminalDetail(sessionId, taskId, currentUserId, detail);
            if ("success".equals(terminalDetail.getTaskStatus()) && terminalDetail.getAssistantMessage() != null) {
                if (!pythonDeltaForwarded.get() && shouldSendFallbackDeltas(afterEventSeq)) {
                    sendDeltas(emitter, terminalDetail.getAssistantMessage());
                }
                sendEvent(emitter, "sources", terminalDetail.getAssistantMessage().getSources());
                sendEvent(emitter, "message", terminalDetail.getAssistantMessage());
                sendEvent(emitter, "done", Map.of("taskId", taskId, "taskStatus", "success"));
            } else {
                sendEvent(emitter, "error", Map.of(
                        "taskId", taskId,
                        "taskStatus", terminalDetail.getTaskStatus(),
                        "message", terminalErrorMessage(terminalDetail)
                ));
            }
            cancelScheduled(futureRef);
            emitter.complete();
        } catch (IOException ex) {
            cancelScheduled(futureRef);
            log.debug("QA 任务事件流连接已不可写, sessionId={}, taskId={}: {}", sessionId, taskId, ex.getMessage());
            completeQuietly(emitter);
        } catch (RuntimeException ex) {
            cancelScheduled(futureRef);
            log.warn("QA 任务事件推送失败, sessionId={}, taskId={}", sessionId, taskId, ex);
            sendErrorEventAndComplete(emitter, taskId, "QA 任务事件流中断，前端可回退到轮询");
        }
    }

    private boolean shouldSendFallbackDeltas(Long afterEventSeq) {
        return afterEventSeq == null || afterEventSeq <= 0L;
    }

    private void startPythonBridgeIfAvailable(
            Long sessionId,
            Long taskId,
            SseEmitter emitter,
            AtomicReference<String> bridgedPythonTaskId,
            AtomicBoolean pythonDeltaForwarded,
            Long afterEventSeq
    ) {
        if (bridgedPythonTaskId.get() != null) {
            return;
        }
        QaRetrievalLogs task;
        try {
            task = qaRetrievalLogsService.getRequiredTask(sessionId, taskId);
        } catch (RuntimeException ex) {
            log.debug("暂未能读取 QA task 以建立 Python 事件桥接, taskId={}", taskId, ex);
            return;
        }
        if (task == null) {
            return;
        }
        String pythonTaskId = task.getPythonTaskId();
        if (pythonTaskId == null || pythonTaskId.isBlank()) {
            return;
        }
        if (!bridgedPythonTaskId.compareAndSet(null, pythonTaskId)) {
            return;
        }
        qaTaskExecutor.execute(() -> {
            try {
                graphRagTaskClient.streamTaskEvents(
                        pythonTaskId,
                        afterEventSeq,
                        event -> forwardPythonEvent(emitter, event, pythonDeltaForwarded)
                );
            } catch (RuntimeException ex) {
                log.debug("Python QA 事件流不可用，继续使用 Java 阶段 1 事件流, pythonTaskId={}", pythonTaskId, ex);
            }
        });
    }

    private void forwardPythonEvent(SseEmitter emitter, GraphRagTaskEvent event, AtomicBoolean pythonDeltaForwarded) {
        if (!"delta".equals(event.eventName()) || event.data() == null || !event.data().has("text")) {
            return;
        }
        String text = event.data().get("text").asText("");
        if (text.isBlank()) {
            return;
        }
        try {
            pythonDeltaForwarded.set(true);
            Long eventSeq = event.eventSeq();
            if (eventSeq == null && event.data().has("eventSeq")) {
                eventSeq = event.data().get("eventSeq").asLong();
            }
            if (eventSeq == null) {
                sendEvent(emitter, "delta", Map.of("text", text));
            } else {
                sendEvent(emitter, "delta", Map.of("text", text, "eventSeq", eventSeq), eventSeq);
            }
        } catch (IOException ex) {
            log.debug("转发 Python QA delta 失败: {}", ex.getMessage());
        }
    }

    private QaTaskDetailResponse resolveTerminalDetail(
            Long sessionId,
            Long taskId,
            Long currentUserId,
            QaTaskDetailResponse detail
    ) {
        QaTaskDetailResponse latest = detail;
        if (!"success".equals(latest.getTaskStatus()) || latest.getAssistantMessage() != null) {
            return latest;
        }
        for (int attempt = 0; attempt < properties.emptyAssistantRetryCount(); attempt += 1) {
            sleepQuietly(properties.emptyAssistantRetryDelayMillis());
            latest = qaWorkflowService.getTaskDetail(sessionId, taskId, currentUserId);
            if (latest.getAssistantMessage() != null || !"success".equals(latest.getTaskStatus())) {
                return latest;
            }
        }
        return latest;
    }

    private void sendDeltas(SseEmitter emitter, QaMessageResponse assistantMessage) throws IOException {
        String content = assistantMessage.getContent() == null ? "" : assistantMessage.getContent();
        for (String chunk : splitContent(content, properties.deltaChars())) {
            if (!chunk.isBlank()) {
                sendEvent(emitter, "delta", Map.of("text", chunk));
            }
        }
    }

    private List<String> splitContent(String content, int maxChars) {
        if (content.isBlank()) {
            return List.of();
        }
        java.util.ArrayList<String> chunks = new java.util.ArrayList<>();
        for (String paragraph : content.split("(?<=\\n)", -1)) {
            if (paragraph.length() <= maxChars) {
                chunks.add(paragraph);
                continue;
            }
            for (int start = 0; start < paragraph.length(); start += maxChars) {
                chunks.add(paragraph.substring(start, Math.min(start + maxChars, paragraph.length())));
            }
        }
        return chunks;
    }

    private void sendHeartbeatIfDue(Long taskId, SseEmitter emitter, AtomicLong lastHeartbeatEpochMillis) throws IOException {
        long now = System.currentTimeMillis();
        long previous = lastHeartbeatEpochMillis.get();
        if (previous == 0L || now - previous >= properties.heartbeatSeconds() * 1000L) {
            lastHeartbeatEpochMillis.set(now);
            sendEvent(emitter, "heartbeat", Map.of(
                    "taskId", taskId,
                    "serverTime", LocalDateTime.now().toString()
            ));
        }
    }

    private boolean isTerminal(String status) {
        return "success".equals(status) || "failed".equals(status) || "stale".equals(status);
    }

    private String terminalErrorMessage(QaTaskDetailResponse detail) {
        if ("stale".equals(detail.getTaskStatus())) {
            return detail.getTimeoutMessage() == null ? "任务心跳超时，请稍后重试" : detail.getTimeoutMessage();
        }
        if ("success".equals(detail.getTaskStatus()) && detail.getAssistantMessage() == null) {
            return "任务已完成，但回答消息仍在装配中，前端可回退到轮询刷新";
        }
        return detail.getErrorMessage() == null ? "问答任务执行失败" : detail.getErrorMessage();
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) throws IOException {
        sendEvent(emitter, eventName, data, null);
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data, Long eventSeq) throws IOException {
        synchronized (emitter) {
            SseEmitter.SseEventBuilder builder = SseEmitter.event().name(eventName).data(data);
            if (eventSeq != null) {
                builder.id(String.valueOf(eventSeq));
            }
            emitter.send(builder);
        }
    }

    private void sendErrorEventAndComplete(SseEmitter emitter, Long taskId, String message) {
        try {
            sendEvent(emitter, "error", Map.of(
                    "taskId", taskId,
                    "taskStatus", "failed",
                    "message", message
            ));
        } catch (IOException ex) {
            log.debug("QA 任务事件流 error 事件发送失败, taskId={}: {}", taskId, ex.getMessage());
        } finally {
            completeQuietly(emitter);
        }
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (RuntimeException ex) {
            log.debug("QA 任务事件流关闭失败: {}", ex.getMessage());
        }
    }

    private void cancelScheduled(AtomicReference<ScheduledFuture<?>> futureRef) {
        ScheduledFuture<?> future = futureRef.getAndSet(null);
        if (future != null && !future.isCancelled()) {
            future.cancel(false);
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
