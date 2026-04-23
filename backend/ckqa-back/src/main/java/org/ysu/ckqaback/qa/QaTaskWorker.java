package org.ysu.ckqaback.qa;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.entity.QaMessages;
import org.ysu.ckqaback.entity.QaRetrievalLogs;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.integration.graphrag.GraphRagTaskClient;
import org.ysu.ckqaback.integration.graphrag.GraphRagTaskCreateResult;
import org.ysu.ckqaback.integration.graphrag.GraphRagTaskSnapshot;
import org.ysu.ckqaback.service.QaMessagesService;
import org.ysu.ckqaback.service.QaRetrievalLogsService;
import org.ysu.ckqaback.service.QaSessionsService;

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

    private final TaskExecutor qaTaskExecutor;
    private final GraphRagTaskClient graphRagTaskClient;
    private final QaRetrievalLogsService qaRetrievalLogsService;
    private final QaMessagesService qaMessagesService;
    private final QaSessionsService qaSessionsService;
    private final Function<String, Duration> pollIntervalResolver;
    private final Function<String, Duration> staleThresholdResolver;
    private final Function<String, String> timeoutMessageResolver;
    private final Clock clock;

    @Autowired
    public QaTaskWorker(
            @Qualifier("qaTaskExecutor") TaskExecutor qaTaskExecutor,
            GraphRagTaskClient graphRagTaskClient,
            QaRetrievalLogsService qaRetrievalLogsService,
            QaMessagesService qaMessagesService,
            QaSessionsService qaSessionsService,
            CkqaIntegrationProperties properties
    ) {
        this(
                qaTaskExecutor,
                graphRagTaskClient,
                qaRetrievalLogsService,
                qaMessagesService,
                qaSessionsService,
                mode -> Duration.ofSeconds(properties.resolveQueryTaskModePolicy(mode).recommendedPollingIntervalSeconds()),
                mode -> Duration.ofSeconds(properties.resolveQueryTaskModePolicy(mode).staleTimeoutSeconds()),
                mode -> properties.resolveQueryTaskModePolicy(mode).timeoutMessage(),
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
                mode -> pollInterval,
                mode -> staleThreshold,
                mode -> "任务心跳超时",
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
        this.qaTaskExecutor = qaTaskExecutor;
        this.graphRagTaskClient = graphRagTaskClient;
        this.qaRetrievalLogsService = qaRetrievalLogsService;
        this.qaMessagesService = qaMessagesService;
        this.qaSessionsService = qaSessionsService;
        this.pollIntervalResolver = pollIntervalResolver;
        this.staleThresholdResolver = staleThresholdResolver;
        this.timeoutMessageResolver = timeoutMessageResolver;
        this.clock = clock;
    }

    public void dispatch(Long sessionId, Long taskId) {
        qaTaskExecutor.execute(() -> processTask(sessionId, taskId));
    }

    public void processTask(Long sessionId, Long taskId) {
        QaRetrievalLogs task = qaRetrievalLogsService.getRequiredTask(sessionId, taskId);
        Duration taskPollInterval = pollIntervalResolver.apply(task.getQueryMode());
        Duration taskStaleThreshold = staleThresholdResolver.apply(task.getQueryMode());
        String timeoutMessage = timeoutMessageResolver.apply(task.getQueryMode());
        GraphRagTaskSnapshot latestSnapshot = null;
        try {
            GraphRagTaskCreateResult created = graphRagTaskClient.createTask(task.getQueryMode(), task.getQueryText());
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
                    qaRetrievalLogsService.markSuccess(taskId, assistant.getId(), joinLatestLogs(snapshot.latestLogs()), "success");
                    return;
                }

                if ("failed".equals(snapshot.taskStatus())) {
                    qaRetrievalLogsService.markFailed(taskId, "failed", snapshot.errorMessage(), joinLatestLogs(snapshot.latestLogs()));
                    return;
                }

                if ("running".equals(snapshot.taskStatus()) && !snapshot.processAlive()) {
                    qaRetrievalLogsService.markFailed(
                            taskId,
                            "failed",
                            "Python 任务进程已结束但未返回终态",
                            joinLatestLogs(snapshot.latestLogs())
                    );
                    return;
                }

                LocalDateTime heartbeatAt = snapshot.lastHeartbeatAt() == null ? snapshot.startedAt() : snapshot.lastHeartbeatAt();
                if (heartbeatAt != null
                        && Duration.between(heartbeatAt, LocalDateTime.now(clock)).compareTo(taskStaleThreshold) > 0) {
                    qaRetrievalLogsService.markFailed(taskId, "stale", timeoutMessage, joinLatestLogs(snapshot.latestLogs()));
                    return;
                }

                try {
                    Thread.sleep(taskPollInterval.toMillis());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    qaRetrievalLogsService.markFailed(taskId, "failed", "Java 后台任务被中断", joinLatestLogs(snapshot.latestLogs()));
                    return;
                }
            }
        } catch (RuntimeException exception) {
            qaRetrievalLogsService.markFailed(
                    taskId,
                    "failed",
                    exception.getMessage() == null ? "Java 后台任务执行失败" : exception.getMessage(),
                    latestSnapshot == null ? "" : joinLatestLogs(latestSnapshot.latestLogs())
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
}
