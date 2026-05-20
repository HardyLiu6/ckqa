package org.ysu.ckqaback.qa.dto;

import lombok.Getter;
import org.ysu.ckqaback.qa.ops.QaOperationLogRow;

import java.time.LocalDateTime;

/**
 * 管理端问答任务列表项。
 */
@Getter
public class QaOperationLogResponse {
    private final Long retrievalLogId;
    private final Long sessionId;
    private final Long userMessageId;
    private final Long assistantMessageId;
    private final Long userId;
    private final String username;
    private final String displayName;
    private final String courseId;
    private final String courseName;
    private final Long knowledgeBaseId;
    private final String knowledgeBaseName;
    private final Long indexRunId;
    private final String sessionTitle;
    private final String sessionType;
    private final String sessionStatus;
    private final String queryMode;
    private final String taskStatus;
    private final String progressStage;
    private final String retrievalStatus;
    private final String contextStrategy;
    private final Boolean rewriteApplied;
    private final String rewriteMethod;
    private final Double rewriteConfidence;
    private final Double routingConfidence;
    private final String routingConfidenceBand;
    private final String routingReviewPriority;
    private final Boolean memoryApplied;
    private final String memoryStrategy;
    private final Integer memorySourceCount;
    private final Integer memorySizeChars;
    private final String queryEngineStrategy;
    private final String historyFallbackReason;
    private final LocalDateTime createdAt;
    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;
    private final Long durationMs;
    private final int sourceCount;
    private final QaFeedbackSummaryResponse feedbackSummary;

    protected QaOperationLogResponse(QaOperationLogRow row) {
        this.retrievalLogId = row.getRetrievalLogId();
        this.sessionId = row.getSessionId();
        this.userMessageId = row.getUserMessageId();
        this.assistantMessageId = row.getAssistantMessageId();
        this.userId = row.getUserId();
        this.username = row.getUsername();
        this.displayName = row.getDisplayName();
        this.courseId = row.getCourseId();
        this.courseName = row.getCourseName();
        this.knowledgeBaseId = row.getKnowledgeBaseId();
        this.knowledgeBaseName = row.getKnowledgeBaseName();
        this.indexRunId = row.getIndexRunId();
        this.sessionTitle = row.getSessionTitle();
        this.sessionType = row.getSessionType();
        this.sessionStatus = row.getSessionStatus();
        this.queryMode = row.getQueryMode();
        this.taskStatus = row.getTaskStatus();
        this.progressStage = row.getProgressStage();
        this.retrievalStatus = row.getRetrievalStatus();
        this.contextStrategy = row.getContextStrategy();
        this.rewriteApplied = row.getRewriteApplied();
        this.rewriteMethod = row.getRewriteMethod();
        this.rewriteConfidence = row.getRewriteConfidence();
        this.routingConfidence = row.getRoutingConfidence();
        this.routingConfidenceBand = row.getRoutingConfidenceBand();
        this.routingReviewPriority = row.getRoutingReviewPriority();
        this.memoryApplied = row.getMemoryApplied();
        this.memoryStrategy = row.getMemoryStrategy();
        this.memorySourceCount = row.getMemorySourceCount();
        this.memorySizeChars = row.getMemorySizeChars();
        this.queryEngineStrategy = row.getQueryEngineStrategy();
        this.historyFallbackReason = row.getHistoryFallbackReason();
        this.createdAt = row.getCreatedAt();
        this.startedAt = row.getStartedAt();
        this.finishedAt = row.getFinishedAt();
        this.durationMs = row.getDurationMs();
        this.sourceCount = row.getSourceCount() == null ? 0 : row.getSourceCount();
        this.feedbackSummary = QaFeedbackSummaryResponse.of(
                row.getHelpfulCount(),
                row.getUnhelpfulCount(),
                row.getNeedsImprovementCount(),
                row.getSourceIssueCount()
        );
    }

    public static QaOperationLogResponse fromRow(QaOperationLogRow row) {
        return new QaOperationLogResponse(row);
    }
}
