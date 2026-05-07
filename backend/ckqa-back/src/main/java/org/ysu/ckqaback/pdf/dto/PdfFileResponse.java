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
    private final String parseStage;
    private final Integer parseProgressPercent;
    private final ParseProgressResponse parseProgress;
    private final LocalDateTime parseStartedAt;
    private final LocalDateTime parseFinishedAt;
    private final String parseErrorMsg;
    private final String mineruBatchId;

    private PdfFileResponse(
            Long id,
            Long materialId,
            Long materialObjectId,
            String courseId,
            String fileName,
            String parseStatus,
            ParseProgressResponse parseProgress,
            LocalDateTime parseStartedAt,
            LocalDateTime parseFinishedAt,
            String parseErrorMsg,
            String mineruBatchId
    ) {
        this.id = id;
        this.materialId = materialId;
        this.materialObjectId = materialObjectId;
        this.courseId = courseId;
        this.fileName = fileName;
        this.parseStatus = parseStatus;
        this.parseProgress = parseProgress;
        this.parseStage = parseProgress == null ? null : parseProgress.getStage();
        this.parseProgressPercent = parseProgress == null ? null : parseProgress.getPercent();
        this.parseStartedAt = parseStartedAt;
        this.parseFinishedAt = parseFinishedAt;
        this.parseErrorMsg = parseErrorMsg;
        this.mineruBatchId = mineruBatchId;
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
        return of(id, materialId, materialObjectId, courseId, fileName, parseStatus, parseStartedAt, parseFinishedAt, parseErrorMsg, null);
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
            String parseErrorMsg,
            String mineruBatchId
    ) {
        CourseMaterials material = new CourseMaterials();
        material.setId(id);
        material.setCourseId(courseId);
        material.setDisplayName(fileName);
        material.setParseStatus(parseStatus);
        material.setParseStartedAt(parseStartedAt);
        material.setParseFinishedAt(parseFinishedAt);
        material.setParseErrorMsg(parseErrorMsg);
        material.setMineruBatchId(mineruBatchId);
        return new PdfFileResponse(
                id,
                materialId,
                materialObjectId,
                courseId,
                fileName,
                parseStatus,
                ParseProgressResponse.fromMaterial(material),
                parseStartedAt,
                parseFinishedAt,
                parseErrorMsg,
                mineruBatchId
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
        return new PdfFileResponse(
                material.getId(),
                material.getId(),
                material.getMaterialObjectId(),
                material.getCourseId(),
                material.getDisplayName(),
                material.getParseStatus(),
                ParseProgressResponse.fromMaterial(material),
                material.getParseStartedAt(),
                material.getParseFinishedAt(),
                material.getParseErrorMsg(),
                material.getMineruBatchId()
        );
    }
}
