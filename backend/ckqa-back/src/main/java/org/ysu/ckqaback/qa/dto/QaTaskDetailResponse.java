package org.ysu.ckqaback.qa.dto;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 异步问答任务详情响应。
 */
@Getter
public class QaTaskDetailResponse {

    private final Long taskId;
    private final Long userMessageId;
    private final Long assistantMessageId;
    private final String taskStatus;
    private final String progressStage;
    private final String retrievalStatus;
    private final String mode;
    private final String queryText;
    private final List<String> latestLogs;
    private final LocalDateTime startedAt;
    private final LocalDateTime lastHeartbeatAt;
    private final LocalDateTime finishedAt;
    private final QaMessageResponse assistantMessage;
    private final String errorMessage;

    private QaTaskDetailResponse(
            Long taskId,
            Long userMessageId,
            Long assistantMessageId,
            String taskStatus,
            String progressStage,
            String retrievalStatus,
            String mode,
            String queryText,
            List<String> latestLogs,
            LocalDateTime startedAt,
            LocalDateTime lastHeartbeatAt,
            LocalDateTime finishedAt,
            QaMessageResponse assistantMessage,
            String errorMessage
    ) {
        this.taskId = taskId;
        this.userMessageId = userMessageId;
        this.assistantMessageId = assistantMessageId;
        this.taskStatus = taskStatus;
        this.progressStage = progressStage;
        this.retrievalStatus = retrievalStatus;
        this.mode = mode;
        this.queryText = queryText;
        this.latestLogs = latestLogs;
        this.startedAt = startedAt;
        this.lastHeartbeatAt = lastHeartbeatAt;
        this.finishedAt = finishedAt;
        this.assistantMessage = assistantMessage;
        this.errorMessage = errorMessage;
    }

    public static QaTaskDetailResponse of(
            Long taskId,
            Long userMessageId,
            Long assistantMessageId,
            String taskStatus,
            String progressStage,
            String retrievalStatus,
            String mode,
            String queryText,
            List<String> latestLogs,
            LocalDateTime startedAt,
            LocalDateTime lastHeartbeatAt,
            LocalDateTime finishedAt,
            QaMessageResponse assistantMessage,
            String errorMessage
    ) {
        return new QaTaskDetailResponse(
                taskId,
                userMessageId,
                assistantMessageId,
                taskStatus,
                progressStage,
                retrievalStatus,
                mode,
                queryText,
                latestLogs,
                startedAt,
                lastHeartbeatAt,
                finishedAt,
                assistantMessage,
                errorMessage
        );
    }
}
