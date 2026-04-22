package org.ysu.ckqaback.course.dto;

import lombok.Getter;
import org.ysu.ckqaback.entity.PdfFiles;

/**
 * 课程下 PDF 文件摘要响应体。
 */
@Getter
public class CoursePdfFileSummaryResponse {

    private final Long id;
    private final String fileName;
    private final String parseStatus;

    private CoursePdfFileSummaryResponse(Long id, String fileName, String parseStatus) {
        this.id = id;
        this.fileName = fileName;
        this.parseStatus = parseStatus;
    }

    public static CoursePdfFileSummaryResponse of(Long id, String fileName, String parseStatus) {
        return new CoursePdfFileSummaryResponse(id, fileName, parseStatus);
    }

    public static CoursePdfFileSummaryResponse fromEntity(PdfFiles pdfFile) {
        return of(pdfFile.getId(), pdfFile.getFileName(), pdfFile.getParseStatus());
    }
}
