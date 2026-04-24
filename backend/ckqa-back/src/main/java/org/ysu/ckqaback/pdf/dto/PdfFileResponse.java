package org.ysu.ckqaback.pdf.dto;

import lombok.Getter;
import org.ysu.ckqaback.entity.CourseMaterials;

import java.time.LocalDateTime;

/**
 * PDF 文件详情响应体。
 */
@Getter
public class PdfFileResponse {

    private final Long id;
    private final Long materialId;
    private final Long materialObjectId;
    private final String courseId;
    private final String fileName;
    private final String parseStatus;
    private final LocalDateTime parseStartedAt;
    private final LocalDateTime parseFinishedAt;
    private final String parseErrorMsg;

    private PdfFileResponse(
            Long id,
            Long materialId,
            Long materialObjectId,
            String courseId,
            String fileName,
            String parseStatus,
            LocalDateTime parseStartedAt,
            LocalDateTime parseFinishedAt,
            String parseErrorMsg
    ) {
        this.id = id;
        this.materialId = materialId;
        this.materialObjectId = materialObjectId;
        this.courseId = courseId;
        this.fileName = fileName;
        this.parseStatus = parseStatus;
        this.parseStartedAt = parseStartedAt;
        this.parseFinishedAt = parseFinishedAt;
        this.parseErrorMsg = parseErrorMsg;
    }

    public static PdfFileResponse of(
            Long id,
            Long materialId,
            Long materialObjectId,
            String courseId,
            String fileName,
            String parseStatus,
            LocalDateTime parseStartedAt,
            LocalDateTime parseFinishedAt,
            String parseErrorMsg
    ) {
        return new PdfFileResponse(
                id,
                materialId,
                materialObjectId,
                courseId,
                fileName,
                parseStatus,
                parseStartedAt,
                parseFinishedAt,
                parseErrorMsg
        );
    }

    public static PdfFileResponse of(
            Long id,
            String courseId,
            String fileName,
            String parseStatus,
            LocalDateTime parseStartedAt,
            LocalDateTime parseFinishedAt,
            String parseErrorMsg
    ) {
        return of(id, id, null, courseId, fileName, parseStatus, parseStartedAt, parseFinishedAt, parseErrorMsg);
    }

    public static PdfFileResponse fromEntity(CourseMaterials material) {
        return of(
                material.getId(),
                material.getId(),
                material.getMaterialObjectId(),
                material.getCourseId(),
                material.getDisplayName(),
                material.getParseStatus(),
                material.getParseStartedAt(),
                material.getParseFinishedAt(),
                material.getParseErrorMsg()
        );
    }
}
