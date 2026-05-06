package org.ysu.ckqaback.course.dto;

import lombok.Builder;
import lombok.Getter;
import org.ysu.ckqaback.entity.CourseMaterials;
import org.ysu.ckqaback.entity.MaterialObjects;

import java.time.LocalDateTime;

/**
 * 课程资料响应体，兼容旧 PDF 字段别名。
 */
@Getter
@Builder
public class CourseMaterialResponse {

    private final Long id;
    private final Long materialId;
    private final Long pdfFileId;
    private final Long materialObjectId;
    private final String courseId;
    private final String fileName;
    private final String displayName;
    private final String originalFileName;
    private final String materialType;
    private final String parseStatus;
    private final LocalDateTime parseStartedAt;
    private final LocalDateTime parseFinishedAt;
    private final String parseErrorMsg;
    private final String mineruBatchId;
    private final String fileMd5;
    private final Long fileSize;
    private final String mimeType;
    private final LocalDateTime uploadTime;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public static CourseMaterialResponse fromEntity(CourseMaterials material, MaterialObjects object) {
        String displayName = material.getDisplayName();
        String originalFileName = object == null ? null : object.getOriginalFileName();
        return CourseMaterialResponse.builder()
                .id(material.getId())
                .materialId(material.getId())
                .pdfFileId(material.getId())
                .materialObjectId(material.getMaterialObjectId())
                .courseId(material.getCourseId())
                .fileName(displayName)
                .displayName(displayName)
                .originalFileName(originalFileName)
                .materialType(material.getMaterialType())
                .parseStatus(material.getParseStatus())
                .parseStartedAt(material.getParseStartedAt())
                .parseFinishedAt(material.getParseFinishedAt())
                .parseErrorMsg(material.getParseErrorMsg())
                .mineruBatchId(material.getMineruBatchId())
                .fileMd5(object == null ? null : object.getFileMd5())
                .fileSize(object == null ? null : object.getFileSize())
                .mimeType(object == null ? null : object.getMimeType())
                .uploadTime(material.getUploadTime())
                .createdAt(material.getCreatedAt())
                .updatedAt(material.getUpdatedAt())
                .build();
    }
}
