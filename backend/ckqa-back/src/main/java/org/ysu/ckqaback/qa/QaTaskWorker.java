package org.ysu.ckqaback.qa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.IndexArtifacts;
import org.ysu.ckqaback.entity.QaMessages;
import org.ysu.ckqaback.entity.QaRetrievalLogs;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.integration.graphrag.GraphRagConversationMessage;
import org.ysu.ckqaback.integration.graphrag.GraphRagTaskClient;
import org.ysu.ckqaback.integration.graphrag.GraphRagTaskCreateResult;
import org.ysu.ckqaback.integration.graphrag.GraphRagTaskSnapshot;
import org.ysu.ckqaback.qa.memory.QaLearningMemoryCaptureService;
import org.ysu.ckqaback.qa.summary.QaSessionSummaryService;
import org.ysu.ckqaback.service.IndexArtifactsService;
import org.ysu.ckqaback.service.QaMessagesService;
import org.ysu.ckqaback.service.QaRetrievalHitsService;
import org.ysu.ckqaback.service.QaRetrievalLogsService;
import org.ysu.ckqaback.service.QaSessionsService;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * 问答异步任务后台 worker。
 */
@Service
public class QaTaskWorker {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<GraphRagConversationMessage>> HISTORY_TYPE = new TypeReference<>() {
    };

    private final TaskExecutor qaTaskExecutor;
    private final GraphRagTaskClient graphRagTaskClient;
    private final QaRetrievalLogsService qaRetrievalLogsService;
    private final QaMessagesService qaMessagesService;
    private final QaSessionsService qaSessionsService;
    private final IndexArtifactsService indexArtifactsService;
    private final Function<String, Duration> pollIntervalResolver;
    private final Function<String, Duration> staleThresholdResolver;
    private final Function<String, String> timeoutMessageResolver;
    private final Function<String, Boolean> pythonStreamModeResolver;
    private final Clock clock;
    private QaSessionSummaryService qaSessionSummaryService;
    private QaRetrievalHitsService qaRetrievalHitsService;
    private QaLearningMemoryCaptureService qaLearningMemoryCaptureService;

    @Autowired
    public QaTaskWorker(
            @Qualifier("qaTaskExecutor") TaskExecutor qaTaskExecutor,
            GraphRagTaskClient graphRagTaskClient,
            QaRetrievalLogsService qaRetrievalLogsService,
            QaMessagesService qaMessagesService,
            QaSessionsService qaSessionsService,
            IndexArtifactsService indexArtifactsService,
            CkqaIntegrationProperties properties
    ) {
        this(
                qaTaskExecutor,
                graphRagTaskClient,
                qaRetrievalLogsService,
                qaMessagesService,
                qaSessionsService,
                indexArtifactsService,
                mode -> Duration.ofSeconds(properties.resolveQueryTaskModePolicy(mode).recommendedPollingIntervalSeconds()),
                mode -> Duration.ofSeconds(properties.resolveQueryTaskModePolicy(mode).staleTimeoutSeconds()),
                mode -> properties.resolveQueryTaskModePolicy(mode).timeoutMessage(),
                mode -> properties.getStreaming().isPythonStreamModeEnabled(mode),
                Clock.system(SHANGHAI_ZONE)
        );
    }

    QaTaskWorker(
            TaskExecutor qaTaskExecutor,
            GraphRagTaskClient graphRagTaskClient,
            QaRetrievalLogsService qaRetrievalLogsService,
            QaMessagesService qaMessagesService,
            QaSessionsService qaSessionsService,
            Duration pollInterval,
            Duration staleThreshold,
            Clock clock
    ) {
        this(
                qaTaskExecutor,
                graphRagTaskClient,
                qaRetrievalLogsService,
                qaMessagesService,
                qaSessionsService,
                null,
                mode -> pollInterval,
                mode -> staleThreshold,
                mode -> "任务心跳超时",
                mode -> false,
                clock
        );
    }

    QaTaskWorker(
            TaskExecutor qaTaskExecutor,
            GraphRagTaskClient graphRagTaskClient,
            QaRetrievalLogsService qaRetrievalLogsService,
            QaMessagesService qaMessagesService,
            QaSessionsService qaSessionsService,
            Function<String, Duration> pollIntervalResolver,
            Function<String, Duration> staleThresholdResolver,
            Function<String, String> timeoutMessageResolver,
            Clock clock
    ) {
        this(
                qaTaskExecutor,
                graphRagTaskClient,
                qaRetrievalLogsService,
                qaMessagesService,
                qaSessionsService,
                null,
                pollIntervalResolver,
                staleThresholdResolver,
                timeoutMessageResolver,
                mode -> false,
                clock
        );
    }

    QaTaskWorker(
            TaskExecutor qaTaskExecutor,
            GraphRagTaskClient graphRagTaskClient,
            QaRetrievalLogsService qaRetrievalLogsService,
            QaMessagesService qaMessagesService,
            QaSessionsService qaSessionsService,
            IndexArtifactsService indexArtifactsService,
            Function<String, Duration> pollIntervalResolver,
            Function<String, Duration> staleThresholdResolver,
            Function<String, String> timeoutMessageResolver,
            Clock clock
    ) {
        this(
                qaTaskExecutor,
                graphRagTaskClient,
                qaRetrievalLogsService,
                qaMessagesService,
                qaSessionsService,
                indexArtifactsService,
                pollIntervalResolver,
                staleThresholdResolver,
                timeoutMessageResolver,
                mode -> false,
                clock
        );
    }

    QaTaskWorker(
            TaskExecutor qaTaskExecutor,
            GraphRagTaskClient graphRagTaskClient,
            QaRetrievalLogsService qaRetrievalLogsService,
            QaMessagesService qaMessagesService,
            QaSessionsService qaSessionsService,
            IndexArtifactsService indexArtifactsService,
            Function<String, Duration> pollIntervalResolver,
            Function<String, Duration> staleThresholdResolver,
            Function<String, String> timeoutMessageResolver,
            Function<String, Boolean> pythonStreamModeResolver,
            Clock clock
    ) {
        this.qaTaskExecutor = qaTaskExecutor;
        this.graphRagTaskClient = graphRagTaskClient;
        this.qaRetrievalLogsService = qaRetrievalLogsService;
        this.qaMessagesService = qaMessagesService;
        this.qaSessionsService = qaSessionsService;
        this.indexArtifactsService = indexArtifactsService;
        this.pollIntervalResolver = pollIntervalResolver;
        this.staleThresholdResolver = staleThresholdResolver;
        this.timeoutMessageResolver = timeoutMessageResolver;
        this.pythonStreamModeResolver = pythonStreamModeResolver;
        this.clock = clock;
    }

    public void dispatch(Long sessionId, Long taskId) {
        qaTaskExecutor.execute(() -> processTask(sessionId, taskId));
    }

    @Autowired(required = false)
    public void setQaSessionSummaryService(QaSessionSummaryService qaSessionSummaryService) {
        this.qaSessionSummaryService = qaSessionSummaryService;
    }

    @Autowired(required = false)
    public void setQaRetrievalHitsService(QaRetrievalHitsService qaRetrievalHitsService) {
        this.qaRetrievalHitsService = qaRetrievalHitsService;
    }

    @Autowired(required = false)
    public void setQaLearningMemoryCaptureService(QaLearningMemoryCaptureService qaLearningMemoryCaptureService) {
        this.qaLearningMemoryCaptureService = qaLearningMemoryCaptureService;
    }

    public void processTask(Long sessionId, Long taskId) {
        GraphRagTaskSnapshot latestSnapshot = null;
        try {
            QaRetrievalLogs task = qaRetrievalLogsService.getRequiredTask(sessionId, taskId);
            Duration taskPollInterval = pollIntervalResolver.apply(task.getQueryMode());
            Duration taskStaleThreshold = staleThresholdResolver.apply(task.getQueryMode());
            String timeoutMessage = timeoutMessageResolver.apply(task.getQueryMode());
            GraphRagTaskCreateResult created = createGraphRagTask(task);
            qaRetrievalLogsService.bindPythonTask(taskId, created.pythonTaskId(), created.taskStatus(), created.progressStage());

            while (true) {
                Optional<GraphRagTaskSnapshot> snapshotOptional = graphRagTaskClient.getTask(created.pythonTaskId());
                if (snapshotOptional.isEmpty()) {
                    qaRetrievalLogsService.markFailed(taskId, "failed", "Python 任务快照丢失或服务已重启", "");
                    return;
                }

                GraphRagTaskSnapshot snapshot = snapshotOptional.get();
                latestSnapshot = snapshot;
                qaRetrievalLogsService.syncRunningSnapshot(taskId, snapshot);

                if ("success".equals(snapshot.taskStatus())) {
                    QaMessages assistant = qaMessagesService.appendAssistantMessage(sessionId, snapshot.resultText());
                    qaSessionsService.touchLastMessageAt(sessionId);
                    persistSources(taskId, snapshot);
                    qaRetrievalLogsService.markSuccess(taskId, assistant.getId(), serializeProgressEventsOrLogs(snapshot), "success");
                    triggerSummaryRefresh(sessionId);
                    triggerLearningMemoryCapture(task, assistant);
                    return;
                }

                if ("failed".equals(snapshot.taskStatus())) {
                    qaRetrievalLogsService.markFailed(taskId, "failed", snapshot.errorMessage(), serializeProgressEventsOrLogs(snapshot));
                    return;
                }

                if ("running".equals(snapshot.taskStatus()) && !snapshot.processAlive()) {
                    qaRetrievalLogsService.markFailed(
                            taskId,
                            "failed",
                            "Python 任务进程已结束但未返回终态",
                            serializeProgressEventsOrLogs(snapshot)
                    );
                    return;
                }

                LocalDateTime heartbeatAt = snapshot.lastHeartbeatAt() == null ? snapshot.startedAt() : snapshot.lastHeartbeatAt();
                if (heartbeatAt != null
                        && Duration.between(heartbeatAt, LocalDateTime.now(clock)).compareTo(taskStaleThreshold) > 0) {
                    qaRetrievalLogsService.markFailed(taskId, "stale", timeoutMessage, serializeProgressEventsOrLogs(snapshot));
                    return;
                }

                try {
                    Thread.sleep(taskPollInterval.toMillis());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    qaRetrievalLogsService.markFailed(taskId, "failed", "Java 后台任务被中断", serializeProgressEventsOrLogs(snapshot));
                    return;
                }
            }
        } catch (RuntimeException exception) {
            qaRetrievalLogsService.markFailed(
                    taskId,
                    "failed",
                    exception.getMessage() == null ? "Java 后台任务执行失败" : exception.getMessage(),
                    latestSnapshot == null ? "" : serializeProgressEventsOrLogs(latestSnapshot)
            );
            throw exception;
        }
    }

    private String joinLatestLogs(List<String> latestLogs) {
        if (latestLogs == null || latestLogs.isEmpty()) {
            return "";
        }
        return String.join("\n", latestLogs);
    }

    private String serializeProgressEventsOrLogs(GraphRagTaskSnapshot snapshot) {
        if (snapshot.progressEvents() != null && !snapshot.progressEvents().isEmpty()) {
            try {
                return OBJECT_MAPPER.writeValueAsString(snapshot.progressEvents());
            } catch (JsonProcessingException ex) {
                return joinLatestLogs(snapshot.latestLogs());
            }
        }
        return joinLatestLogs(snapshot.latestLogs());
    }

    private void triggerSummaryRefresh(Long sessionId) {
        if (qaSessionSummaryService == null) {
            return;
        }
        try {
            qaSessionSummaryService.checkAndSummarizeAsync(sessionId);
        } catch (RuntimeException ignored) {
            // 摘要是旁路增强，不能影响主问答任务成功。
        }
    }

    private void triggerLearningMemoryCapture(QaRetrievalLogs task, QaMessages assistant) {
        if (qaLearningMemoryCaptureService == null) {
            return;
        }
        try {
            qaLearningMemoryCaptureService.captureAfterAssistantSuccess(task, assistant);
        } catch (RuntimeException ignored) {
            // 长期记忆是旁路增强，不能影响主问答任务成功。
        }
    }

    private void persistSources(Long taskId, GraphRagTaskSnapshot snapshot) {
        if (qaRetrievalHitsService == null) {
            return;
        }
        try {
            qaRetrievalHitsService.replaceHits(taskId, snapshot.sources());
        } catch (RuntimeException ignored) {
            // 来源卡片是可观测增强，不能影响主问答成功。
        }
    }

    private GraphRagTaskCreateResult createGraphRagTask(QaRetrievalLogs task) {
        String dataDirUri = resolveReadyOutputDirUri(task);
        List<GraphRagConversationMessage> conversationHistory = parseMemoryHistory(task.getMemoryHistoryJson());
        boolean useHistoryStrategy = StringUtils.hasText(task.getQueryEngineStrategy()) || !conversationHistory.isEmpty();
        boolean streamResponse = Boolean.TRUE.equals(pythonStreamModeResolver.apply(task.getQueryMode()));
        if (!StringUtils.hasText(dataDirUri)) {
            if (!useHistoryStrategy && !streamResponse) {
                return graphRagTaskClient.createTask(
                        task.getQueryMode(),
                        task.getQueryText(),
                        null,
                        null,
                        task.getContextSnapshotText()
                );
            }
            return graphRagTaskClient.createTask(
                    task.getQueryMode(),
                    task.getQueryText(),
                    null,
                    null,
                    task.getContextSnapshotText(),
                    useHistoryStrategy ? task.getQueryEngineStrategy() : null,
                    useHistoryStrategy ? conversationHistory : null,
                    streamResponse
            );
        }
        if (!useHistoryStrategy) {
            if (!streamResponse) {
                return graphRagTaskClient.createTask(
                        task.getQueryMode(),
                        task.getQueryText(),
                        task.getIndexRunId(),
                        dataDirUri,
                        task.getContextSnapshotText()
                );
            }
            return graphRagTaskClient.createTask(
                    task.getQueryMode(),
                    task.getQueryText(),
                    task.getIndexRunId(),
                    dataDirUri,
                    task.getContextSnapshotText(),
                    null,
                    null,
                    streamResponse
            );
        }
        return graphRagTaskClient.createTask(
                task.getQueryMode(),
                task.getQueryText(),
                task.getIndexRunId(),
                dataDirUri,
                task.getContextSnapshotText(),
                task.getQueryEngineStrategy(),
                conversationHistory,
                streamResponse
        );
    }

    private List<GraphRagConversationMessage> parseMemoryHistory(String memoryHistoryJson) {
        if (!StringUtils.hasText(memoryHistoryJson)) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(memoryHistoryJson, HISTORY_TYPE);
        } catch (JsonProcessingException exception) {
            // 记忆上下文只是增强能力，历史 JSON 损坏时降级为无 history，避免阻断正式问答。
            return List.of();
        }
    }

    private String resolveReadyOutputDirUri(QaRetrievalLogs task) {
        if (task.getIndexRunId() == null || indexArtifactsService == null) {
            return null;
        }
        return indexArtifactsService.listByIndexRunId(task.getIndexRunId()).stream()
                .filter(artifact -> "output_dir".equals(artifact.getArtifactType()))
                .filter(artifact -> "ready".equals(artifact.getArtifactStatus()))
                .map(IndexArtifacts::getStorageUri)
                .filter(this::isSafeRelativeUri)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ApiResultCode.KNOWLEDGE_BASE_NOT_READY, HttpStatus.CONFLICT));
    }

    private boolean isSafeRelativeUri(String storageUri) {
        if (!StringUtils.hasText(storageUri) || storageUri.contains("\\") || storageUri.contains("/home/")) {
            return false;
        }
        Path path = Path.of(storageUri);
        if (path.isAbsolute()) {
            return false;
        }
        Path normalized = path.normalize();
        return !normalized.startsWith("..");
    }
}
