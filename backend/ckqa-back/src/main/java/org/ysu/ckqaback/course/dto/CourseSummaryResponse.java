package org.ysu.ckqaback.course.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 课程列表摘要响应。
 */
@Getter
public class CourseSummaryResponse {

    private final Long id;
    private final String courseId;
    private final String courseName;
    private final String description;
    private final String coverUrl;
    private final String status;
    private final String accessPolicy;
    private final Long materialCount;
    private final Long parsedMaterialCount;
    private final Long failedMaterialCount;
    private final Long knowledgeBaseCount;
    private final Long activeKnowledgeBaseCount;
    private final Long latestIndexRunId;
    private final String latestIndexRunStatus;
    private final List<CourseTeacherResponse> teachers;
    private final Long teacherCount;
    private final LocalDateTime updatedAt;

    @Builder
    @JsonCreator
    public CourseSummaryResponse(
            @JsonProperty("id") Long id,
            @JsonProperty("courseId") String courseId,
            @JsonProperty("courseName") String courseName,
            @JsonProperty("description") String description,
            @JsonProperty("coverUrl") String coverUrl,
            @JsonProperty("status") String status,
            @JsonProperty("accessPolicy") String accessPolicy,
            @JsonProperty("materialCount") Long materialCount,
            @JsonProperty("parsedMaterialCount") Long parsedMaterialCount,
            @JsonProperty("failedMaterialCount") Long failedMaterialCount,
            @JsonProperty("knowledgeBaseCount") Long knowledgeBaseCount,
            @JsonProperty("activeKnowledgeBaseCount") Long activeKnowledgeBaseCount,
            @JsonProperty("latestIndexRunId") Long latestIndexRunId,
            @JsonProperty("latestIndexRunStatus") String latestIndexRunStatus,
            @JsonProperty("teachers") List<CourseTeacherResponse> teachers,
            @JsonProperty("teacherCount") Long teacherCount,
            @JsonProperty("updatedAt") LocalDateTime updatedAt
    ) {
        this.id = id;
        this.courseId = courseId;
        this.courseName = courseName;
        this.description = description;
        this.coverUrl = coverUrl;
        this.status = status;
        this.accessPolicy = accessPolicy;
        this.materialCount = materialCount;
        this.parsedMaterialCount = parsedMaterialCount;
        this.failedMaterialCount = failedMaterialCount;
        this.knowledgeBaseCount = knowledgeBaseCount;
        this.activeKnowledgeBaseCount = activeKnowledgeBaseCount;
        this.latestIndexRunId = latestIndexRunId;
        this.latestIndexRunStatus = latestIndexRunStatus;
        this.teachers = teachers == null ? List.of() : List.copyOf(teachers);
        this.teacherCount = teacherCount == null ? 0L : teacherCount;
        this.updatedAt = updatedAt;
    }
}
