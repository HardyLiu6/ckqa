package org.ysu.ckqaback.qa.dto;

import lombok.Getter;
import org.ysu.ckqaback.qa.ops.QaOperationLogRow;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端问答任务详情。
 */
@Getter
public class QaOperationLogDetailResponse extends QaOperationLogResponse {
    private final String errorMessage;
    private final String originalQueryText;
    private final String retrievalQueryText;
    private final String standaloneQueryText;
    private final String generationContext;
    private final String contextSnapshotText;
    private final String contextMessageRange;
    private final Integer contextCharCount;
    private final String rewriteReason;
    private final String rewriteModel;
    private final String routingSnapshotJson;
    private final LocalDateTime lastHeartbeatAt;
    private final String assistantContent;
    private final List<QaOperationFeedbackResponse> feedback;
    private final List<QaOperationSourceResponse> sources;

    private QaOperationLogDetailResponse(
            QaOperationLogRow row,
            List<QaOperationFeedbackResponse> feedback,
            List<QaOperationSourceResponse> sources
    ) {
        super(row);
        this.errorMessage = row.getErrorMessage();
        this.originalQueryText = row.getOriginalQueryText();
        this.retrievalQueryText = row.getRetrievalQueryText();
        this.standaloneQueryText = row.getStandaloneQueryText();
        this.generationContext = row.getGenerationContext();
        this.contextSnapshotText = row.getContextSnapshotText();
        this.contextMessageRange = row.getContextMessageRange();
        this.contextCharCount = row.getContextCharCount();
        this.rewriteReason = row.getRewriteReason();
        this.rewriteModel = row.getRewriteModel();
        this.routingSnapshotJson = row.getRoutingSnapshotJson();
        this.lastHeartbeatAt = row.getLastHeartbeatAt();
        this.assistantContent = row.getAssistantContent();
        this.feedback = feedback == null ? List.of() : List.copyOf(feedback);
        this.sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public static QaOperationLogDetailResponse of(
            QaOperationLogRow row,
            List<QaOperationFeedbackResponse> feedback,
            List<QaOperationSourceResponse> sources
    ) {
        return new QaOperationLogDetailResponse(row, feedback, sources);
    }
}
