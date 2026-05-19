package org.ysu.ckqaback.qa.dto;

import lombok.Getter;
import org.ysu.ckqaback.entity.QaMessageFeedback;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学生端问答反馈响应。
 */
@Getter
public class QaFeedbackResponse {

    private final Long id;
    private final Long messageId;
    private final Long retrievalLogId;
    private final String rating;
    private final List<String> tags;
    private final String comment;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private QaFeedbackResponse(
            Long id,
            Long messageId,
            Long retrievalLogId,
            String rating,
            List<String> tags,
            String comment,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.messageId = messageId;
        this.retrievalLogId = retrievalLogId;
        this.rating = rating;
        this.tags = tags == null ? List.of() : List.copyOf(tags);
        this.comment = comment;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static QaFeedbackResponse of(
            Long id,
            Long messageId,
            Long retrievalLogId,
            String rating,
            List<String> tags,
            String comment,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        return new QaFeedbackResponse(id, messageId, retrievalLogId, rating, tags, comment, createdAt, updatedAt);
    }

    public static QaFeedbackResponse fromEntity(QaMessageFeedback feedback, List<String> tags) {
        if (feedback == null) {
            return null;
        }
        return of(
                feedback.getId(),
                feedback.getMessageId(),
                feedback.getRetrievalLogId(),
                feedback.getRating(),
                tags,
                feedback.getComment(),
                feedback.getCreatedAt(),
                feedback.getUpdatedAt()
        );
    }
}
