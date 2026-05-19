package org.ysu.ckqaback.qa.dto;

import lombok.Getter;
import org.ysu.ckqaback.qa.ops.QaOperationFeedbackRow;
import org.ysu.ckqaback.service.impl.QaMessageFeedbackServiceImpl;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端问答反馈响应。
 */
@Getter
public class QaOperationFeedbackResponse {
    private final Long id;
    private final Long messageId;
    private final Long retrievalLogId;
    private final Long userId;
    private final String username;
    private final String displayName;
    private final String rating;
    private final List<String> tags;
    private final String comment;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private QaOperationFeedbackResponse(QaOperationFeedbackRow row) {
        this.id = row.getId();
        this.messageId = row.getMessageId();
        this.retrievalLogId = row.getRetrievalLogId();
        this.userId = row.getUserId();
        this.username = row.getUsername();
        this.displayName = row.getDisplayName();
        this.rating = row.getRating();
        this.tags = QaMessageFeedbackServiceImpl.readTags(row.getTags());
        this.comment = row.getComment();
        this.createdAt = row.getCreatedAt();
        this.updatedAt = row.getUpdatedAt();
    }

    public static QaOperationFeedbackResponse fromRow(QaOperationFeedbackRow row) {
        return new QaOperationFeedbackResponse(row);
    }
}
