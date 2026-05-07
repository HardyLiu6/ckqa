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
    private final Long courseMaterialId;
    private final Long pdfFileId;
    private final String courseId;
    private final String resultType;
    private final String fileName;
    private final String minioBucket;
    private final String minioObjectKey;
    private final Long fileSize;
    private final LocalDateTime createdAt;
    private final String contentType;
    private final boolean previewable;
    private final String previewUrl;
    private final String downloadUrl;

    private ParseResultResponse(
            Long id,
            Long courseMaterialId,
            Long pdfFileId,
            String courseId,
            String resultType,
            String fileName,
            String minioBucket,
            String minioObjectKey,
            Long fileSize,
            LocalDateTime createdAt,
            String contentType,
            boolean previewable,
            String previewUrl,
            String downloadUrl
    ) {
        this.id = id;
        this.courseMaterialId = courseMaterialId;
        this.pdfFileId = pdfFileId;
        this.courseId = courseId;
        this.resultType = resultType;
        this.fileName = fileName;
        this.minioBucket = minioBucket;
        this.minioObjectKey = minioObjectKey;
        this.fileSize = fileSize;
        this.createdAt = createdAt;
        this.contentType = contentType;
        this.previewable = previewable;
        this.previewUrl = previewUrl;
        this.downloadUrl = downloadUrl;
    }

    public static ParseResultResponse fromEntity(ParseResults parseResult) {
        String contentType = inferContentType(parseResult.getFileName());
        boolean previewable = isPreviewable(contentType);
        Long courseMaterialId = parseResult.getCourseMaterialId();
        Long resultId = parseResult.getId();
        return new ParseResultResponse(
                resultId,
                courseMaterialId,
                courseMaterialId,
                parseResult.getCourseId(),
                parseResult.getResultType(),
                parseResult.getFileName(),
                parseResult.getMinioBucket(),
                parseResult.getMinioObjectKey(),
                parseResult.getFileSize(),
                parseResult.getCreatedAt(),
                contentType,
                previewable,
                buildAccessUrl(courseMaterialId, resultId, "preview"),
                buildAccessUrl(courseMaterialId, resultId, "download")
        );
    }

    public static String inferContentType(String fileName) {
        String normalized = fileName == null ? "" : fileName.trim().toLowerCase();
        if (normalized.endsWith(".json")) {
            return "application/json";
        }
        if (normalized.endsWith(".md") || normalized.endsWith(".markdown")) {
            return "text/markdown";
        }
        if (normalized.endsWith(".txt") || normalized.endsWith(".log")) {
            return "text/plain";
        }
        if (normalized.endsWith(".html") || normalized.endsWith(".htm")) {
            return "text/html";
        }
        if (normalized.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (normalized.endsWith(".png")) {
            return "image/png";
        }
        if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (normalized.endsWith(".webp")) {
            return "image/webp";
        }
        return "application/octet-stream";
    }

    private static boolean isPreviewable(String contentType) {
        return contentType != null
                && (contentType.startsWith("text/")
                || "application/json".equals(contentType)
                || "application/pdf".equals(contentType)
                || contentType.startsWith("image/"));
    }

    private static String buildAccessUrl(Long courseMaterialId, Long resultId, String action) {
        if (courseMaterialId == null || resultId == null) {
            return null;
        }
        return "/api/v1/pdf-files/" + courseMaterialId + "/results/" + resultId + "/" + action;
    }
}
