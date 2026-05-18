package org.ysu.ckqaback.qa.dto;

import lombok.Getter;
import org.ysu.ckqaback.entity.QaSourceReviews;

import java.time.LocalDateTime;

/**
 * 问答来源人工标注响应。
 */
@Getter
public class QaSourceReviewResponse {

    private final Long id;
    private final Long retrievalHitId;
    private final Long retrievalLogId;
    private final Long reviewerUserId;
    private final String relevance;
    private final String citationQuality;
    private final String note;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private QaSourceReviewResponse(
            Long id,
            Long retrievalHitId,
            Long retrievalLogId,
            Long reviewerUserId,
            String relevance,
            String citationQuality,
            String note,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.retrievalHitId = retrievalHitId;
        this.retrievalLogId = retrievalLogId;
        this.reviewerUserId = reviewerUserId;
        this.relevance = relevance;
        this.citationQuality = citationQuality;
        this.note = note;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static QaSourceReviewResponse fromEntity(QaSourceReviews review) {
        if (review == null) {
            return null;
        }
        return new QaSourceReviewResponse(
                review.getId(),
                review.getRetrievalHitId(),
                review.getRetrievalLogId(),
                review.getReviewerUserId(),
                review.getRelevance(),
                review.getCitationQuality(),
                review.getNote(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }
}
