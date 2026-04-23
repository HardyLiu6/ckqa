package org.ysu.ckqaback.pdf.dto;

import lombok.Getter;

/**
 * PDF 操作结果响应体。
 */
@Getter
public class PdfOperationResponse {

    private final Long id;
    private final String courseId;
    private final String fileName;
    private final String parseStatus;
    private final String message;

    private PdfOperationResponse(Long id, String courseId, String fileName, String parseStatus, String message) {
        this.id = id;
        this.courseId = courseId;
        this.fileName = fileName;
        this.parseStatus = parseStatus;
        this.message = message;
    }

    public static PdfOperationResponse success(Long id, String courseId, String fileName, String parseStatus, String message) {
        return new PdfOperationResponse(id, courseId, fileName, parseStatus, message);
    }
}
