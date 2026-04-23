package org.ysu.ckqaback.pdf.dto;

import lombok.Getter;
import org.ysu.ckqaback.entity.PdfFiles;

import java.time.LocalDateTime;

/**
 * PDF 文件详情响应体。
 */
@Getter
public class PdfFileResponse {

    private final Long id;
    private final String courseId;
    private final String fileName;
    private final String parseStatus;
    private final LocalDateTime parseStartedAt;
    private final LocalDateTime parseFinishedAt;
    private final String parseErrorMsg;

    private PdfFileResponse(
            Long id,
            String courseId,
            String fileName,
            String parseStatus,
            LocalDateTime parseStartedAt,
            LocalDateTime parseFinishedAt,
            String parseErrorMsg
    ) {
        this.id = id;
        this.courseId = courseId;
        this.fileName = fileName;
        this.parseStatus = parseStatus;
        this.parseStartedAt = parseStartedAt;
        this.parseFinishedAt = parseFinishedAt;
        this.parseErrorMsg = parseErrorMsg;
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
        return new PdfFileResponse(id, courseId, fileName, parseStatus, parseStartedAt, parseFinishedAt, parseErrorMsg);
    }

    public static PdfFileResponse fromEntity(PdfFiles pdfFile) {
        return of(
                pdfFile.getId(),
                pdfFile.getCourseId(),
                pdfFile.getFileName(),
                pdfFile.getParseStatus(),
                pdfFile.getParseStartedAt(),
                pdfFile.getParseFinishedAt(),
                pdfFile.getParseErrorMsg()
        );
    }
}
