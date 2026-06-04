package org.ysu.ckqaback.qa.dto;

import lombok.Getter;
import org.ysu.ckqaback.entity.QaSessionSummaries;

import java.time.LocalDateTime;

/**
 * transcript 中的最新滚动摘要。
 */
@Getter
public class QaTranscriptSummaryResponse {

    private final String summaryText;
    private final Integer summaryUntilSequenceNo;
    private final Integer sourceMessageCount;
    private final String latestTopic;
    private final String latestTopicMessageRange;
    private final String activeTopicsJson;
    private final String semanticStateVersion;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private QaTranscriptSummaryResponse(
            String summaryText,
            Integer summaryUntilSequenceNo,
            Integer sourceMessageCount,
            String latestTopic,
            String latestTopicMessageRange,
            String activeTopicsJson,
            String semanticStateVersion,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.summaryText = summaryText;
        this.summaryUntilSequenceNo = summaryUntilSequenceNo;
        this.sourceMessageCount = sourceMessageCount;
        this.latestTopic = latestTopic;
        this.latestTopicMessageRange = latestTopicMessageRange;
        this.activeTopicsJson = activeTopicsJson;
        this.semanticStateVersion = semanticStateVersion;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static QaTranscriptSummaryResponse of(
            String summaryText,
            Integer summaryUntilSequenceNo,
            Integer sourceMessageCount,
            String latestTopic,
            String latestTopicMessageRange,
            String activeTopicsJson,
            String semanticStateVersion,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        return new QaTranscriptSummaryResponse(
                summaryText,
                summaryUntilSequenceNo,
                sourceMessageCount,
                latestTopic,
                latestTopicMessageRange,
                activeTopicsJson,
                semanticStateVersion,
                createdAt,
                updatedAt
        );
    }

    public static QaTranscriptSummaryResponse fromEntity(QaSessionSummaries summary) {
        if (summary == null) {
            return null;
        }
        return of(
                summary.getSummaryText(),
                summary.getSummaryUntilSequenceNo(),
                summary.getSourceMessageCount(),
                summary.getLatestTopic(),
                summary.getLatestTopicMessageRange(),
                summary.getActiveTopicsJson(),
                summary.getSemanticStateVersion(),
                summary.getCreatedAt(),
                summary.getUpdatedAt()
        );
    }
}
