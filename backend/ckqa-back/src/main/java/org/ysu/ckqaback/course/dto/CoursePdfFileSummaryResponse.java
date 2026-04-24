package org.ysu.ckqaback.course.dto;

import lombok.Getter;
import org.ysu.ckqaback.entity.CourseMaterials;

/**
 * 课程下 PDF 文件摘要响应体。
 */
@Getter
public class CoursePdfFileSummaryResponse {

    private final Long id;
    private final Long materialId;
    private final Long materialObjectId;
    private final String fileName;
    private final String parseStatus;

    private CoursePdfFileSummaryResponse(Long id, Long materialId, Long materialObjectId, String fileName, String parseStatus) {
        this.id = id;
        this.materialId = materialId;
        this.materialObjectId = materialObjectId;
        this.fileName = fileName;
        this.parseStatus = parseStatus;
    }

    public static CoursePdfFileSummaryResponse of(Long id, Long materialId, Long materialObjectId, String fileName, String parseStatus) {
        return new CoursePdfFileSummaryResponse(id, materialId, materialObjectId, fileName, parseStatus);
    }

    public static CoursePdfFileSummaryResponse of(Long id, String fileName, String parseStatus) {
        return of(id, id, null, fileName, parseStatus);
    }

    public static CoursePdfFileSummaryResponse fromEntity(CourseMaterials material) {
        return of(
                material.getId(),
                material.getId(),
                material.getMaterialObjectId(),
                material.getDisplayName(),
                material.getParseStatus()
        );
    }
}
