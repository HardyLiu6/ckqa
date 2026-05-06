package org.ysu.ckqaback.course.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 课程详情响应。
 */
@Getter
@Builder
public class CourseDetailResponse {

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
    @Builder.Default
    private final List<CourseTeacherResponse> teachers = List.of();
    @Builder.Default
    private final Long teacherCount = 0L;
    private final LocalDateTime updatedAt;
    private final LocalDateTime createdAt;
    private final String accessPolicyDescription;
    private final Long memberCount;
}
