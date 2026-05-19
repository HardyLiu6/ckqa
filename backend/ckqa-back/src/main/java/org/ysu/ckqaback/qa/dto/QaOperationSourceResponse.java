package org.ysu.ckqaback.qa.dto;

import lombok.Getter;
import org.ysu.ckqaback.qa.ops.QaOperationSourceRow;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理端问答来源响应。
 */
@Getter
public class QaOperationSourceResponse {
    private final Long id;
    private final Long retrievalLogId;
    private final Integer rankPosition;
    private final String documentKey;
    private final String chunkId;
    private final String sourceType;
    private final String sourceRef;
    private final String sourceFile;
    private final String headingPath;
    private final Integer pageStart;
    private final Integer pageEnd;
    private final String snippet;
    private final BigDecimal score;
    private final List<QaSourceReviewResponse> reviews;

    private QaOperationSourceResponse(QaOperationSourceRow row, List<QaSourceReviewResponse> reviews) {
        this.id = row.getId();
        this.retrievalLogId = row.getRetrievalLogId();
        this.rankPosition = row.getRankPosition();
        this.documentKey = row.getDocumentKey();
        this.chunkId = row.getChunkId();
        this.sourceType = row.getSourceType();
        this.sourceRef = row.getSourceRef();
        this.sourceFile = row.getSourceFile();
        this.headingPath = row.getHeadingPath();
        this.pageStart = row.getPageStart();
        this.pageEnd = row.getPageEnd();
        this.snippet = row.getSnippet();
        this.score = row.getScore();
        this.reviews = reviews == null ? List.of() : List.copyOf(reviews);
    }

    public static QaOperationSourceResponse fromRow(
            QaOperationSourceRow row,
            List<QaSourceReviewResponse> reviews
    ) {
        return new QaOperationSourceResponse(row, reviews);
    }
}
