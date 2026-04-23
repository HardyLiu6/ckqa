package org.ysu.ckqaback.pdf.dto;

import lombok.Getter;
import org.ysu.ckqaback.entity.ParseResults;

import java.time.LocalDateTime;

/**
 * 解析结果响应体。
 */
@Getter
public class ParseResultResponse {

    private final Long id;
    private final Long pdfFileId;
    private final String courseId;
    private final String resultType;
    private final String fileName;
    private final String minioBucket;
    private final String minioObjectKey;
    private final Long fileSize;
    private final LocalDateTime createdAt;

    private ParseResultResponse(
            Long id,
            Long pdfFileId,
            String courseId,
            String resultType,
            String fileName,
            String minioBucket,
            String minioObjectKey,
            Long fileSize,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.pdfFileId = pdfFileId;
        this.courseId = courseId;
        this.resultType = resultType;
        this.fileName = fileName;
        this.minioBucket = minioBucket;
        this.minioObjectKey = minioObjectKey;
        this.fileSize = fileSize;
        this.createdAt = createdAt;
    }

    public static ParseResultResponse fromEntity(ParseResults parseResult) {
        return new ParseResultResponse(
                parseResult.getId(),
                parseResult.getPdfFileId(),
                parseResult.getCourseId(),
                parseResult.getResultType(),
                parseResult.getFileName(),
                parseResult.getMinioBucket(),
                parseResult.getMinioObjectKey(),
                parseResult.getFileSize(),
                parseResult.getCreatedAt()
        );
    }
}
