package org.ysu.ckqaback.qa.ops;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 问答运维列表/详情查询行。
 */
@Getter
@Setter
public class QaOperationLogRow {
    private Long retrievalLogId;
    private Long sessionId;
    private Long userMessageId;
    private Long assistantMessageId;
    private Long userId;
    private String username;
    private String displayName;
    private String courseId;
    private String courseName;
    private Long knowledgeBaseId;
    private String knowledgeBaseName;
    private Long indexRunId;
    private String sessionTitle;
    private String sessionType;
    private String sessionStatus;
    private String queryMode;
    private String taskStatus;
    private String progressStage;
    private String retrievalStatus;
    private String errorMessage;
    private String originalQueryText;
    private String retrievalQueryText;
    private String standaloneQueryText;
    private String generationContext;
    private String contextSnapshotText;
    private String contextStrategy;
    private String contextMessageRange;
    private Integer contextCharCount;
    private Boolean rewriteApplied;
    private String rewriteReason;
    private String rewriteMethod;
    private String rewriteModel;
    private Double rewriteConfidence;
    private Double routingConfidence;
    private String routingConfidenceBand;
    private String routingReviewPriority;
    private String routingSnapshotJson;
    private Boolean memoryApplied;
    private String memoryStrategy;
    private String memoryScope;
    private Integer memorySourceCount;
    private Integer memorySizeChars;
    private String queryEngineStrategy;
    private String historyFallbackReason;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime lastHeartbeatAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private Integer sourceCount;
    private Integer helpfulCount;
    private Integer unhelpfulCount;
    private Integer needsImprovementCount;
    private Integer sourceIssueCount;
    private String assistantContent;
}
