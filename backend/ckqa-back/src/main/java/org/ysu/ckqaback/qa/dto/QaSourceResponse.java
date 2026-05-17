package org.ysu.ckqaback.qa.dto;

import lombok.Getter;
import org.ysu.ckqaback.entity.QaRetrievalHits;

/**
 * 学生端可展示的问答参考来源。
 */
@Getter
public class QaSourceResponse {

    private final Integer rankPosition;
    private final String documentKey;
    private final String chunkId;
    private final String sourceRef;
    private final String sourceFile;
    private final String headingPath;
    private final Integer pageStart;
    private final Integer pageEnd;
    private final String snippet;

    private QaSourceResponse(
            Integer rankPosition,
            String documentKey,
            String chunkId,
            String sourceRef,
            String sourceFile,
            String headingPath,
            Integer pageStart,
            Integer pageEnd,
            String snippet
    ) {
        this.rankPosition = rankPosition;
        this.documentKey = documentKey;
        this.chunkId = chunkId;
        this.sourceRef = sourceRef;
        this.sourceFile = sourceFile;
        this.headingPath = headingPath;
        this.pageStart = pageStart;
        this.pageEnd = pageEnd;
        this.snippet = snippet;
    }

    public static QaSourceResponse of(
            Integer rankPosition,
            String documentKey,
            String chunkId,
            String sourceRef,
            String sourceFile,
            String headingPath,
            Integer pageStart,
            Integer pageEnd,
            String snippet
    ) {
        return new QaSourceResponse(
                rankPosition,
                documentKey,
                chunkId,
                sourceRef,
                sourceFile,
                headingPath,
                pageStart,
                pageEnd,
                snippet
        );
    }

    public static QaSourceResponse fromEntity(QaRetrievalHits hit) {
        return of(
                hit.getRankPosition(),
                hit.getDocumentKey(),
                hit.getChunkId(),
                hit.getSourceRef(),
                hit.getSourceFile(),
                hit.getHeadingPath(),
                hit.getPageStart(),
                hit.getPageEnd(),
                hit.getSnippet()
        );
    }
}
