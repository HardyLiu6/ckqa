package org.ysu.ckqaback.course;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.course.dto.CourseDetailResponse;
import org.ysu.ckqaback.course.dto.CoursePdfFileSummaryResponse;
import org.ysu.ckqaback.course.dto.CourseQueryRequest;
import org.ysu.ckqaback.course.dto.CourseSummaryResponse;
import org.ysu.ckqaback.entity.CourseMaterials;
import org.ysu.ckqaback.entity.Courses;
import org.ysu.ckqaback.entity.IndexRuns;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.course.dto.KnowledgeBaseSummaryResponse;
import org.ysu.ckqaback.service.CourseMaterialsService;
import org.ysu.ckqaback.service.CoursesService;
import org.ysu.ckqaback.service.IndexRunsService;
import org.ysu.ckqaback.service.KnowledgeBasesService;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * 课程维度查询服务。
 */
@Service
@RequiredArgsConstructor
public class CourseLookupService {

    private final CoursesService coursesService;
    private final CourseMaterialsService courseMaterialsService;
    private final KnowledgeBasesService knowledgeBasesService;
    private final IndexRunsService indexRunsService;

    public ApiPageData<CourseSummaryResponse> listCourses(CourseQueryRequest request) {
        long page = request.getPage() == null ? 1L : Math.max(1L, request.getPage());
        long size = request.getSize() == null ? 20L : Math.max(1L, request.getSize());

        List<CourseSummaryResponse> filtered = coursesService.list().stream()
                .filter(course -> matchesStatus(course, request.getStatus()))
                .filter(course -> matchesKeyword(course, request.getKeyword()))
                .sorted(Comparator.comparing(Courses::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(course -> buildSummary(course, courseMaterialsService.listByCourseId(course.getCourseId()),
                        knowledgeBasesService.listByCourseId(course.getCourseId())))
                .toList();

        long total = filtered.size();
        long offset = (page - 1L) * size;
        long pages = total == 0 ? 0 : (long) Math.ceil((double) total / size);
        if (offset >= total) {
            return new ApiPageData<>(List.of(), page, size, total, pages);
        }

        int fromIndex = (int) offset;
        int toIndex = (int) Math.min(offset + size, total);

        return new ApiPageData<>(filtered.subList(fromIndex, toIndex), page, size, total, pages);
    }

    public CourseDetailResponse getCourseDetail(String courseId) {
        Courses course = coursesService.list().stream()
                .filter(item -> Objects.equals(item.getCourseId(), courseId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.NOT_FOUND, "课程不存在"));
        List<CourseMaterials> materials = courseMaterialsService.listByCourseId(course.getCourseId());
        List<KnowledgeBases> knowledgeBases = knowledgeBasesService.listByCourseId(course.getCourseId());
        CourseSummaryResponse summary = buildSummary(course, materials, knowledgeBases);

        return CourseDetailResponse.builder()
                .id(summary.getId())
                .courseId(summary.getCourseId())
                .courseName(summary.getCourseName())
                .description(summary.getDescription())
                .status(summary.getStatus())
                .accessPolicy(summary.getAccessPolicy())
                .materialCount(summary.getMaterialCount())
                .parsedMaterialCount(summary.getParsedMaterialCount())
                .failedMaterialCount(summary.getFailedMaterialCount())
                .knowledgeBaseCount(summary.getKnowledgeBaseCount())
                .activeKnowledgeBaseCount(summary.getActiveKnowledgeBaseCount())
                .latestIndexRunId(summary.getLatestIndexRunId())
                .latestIndexRunStatus(summary.getLatestIndexRunStatus())
                .updatedAt(summary.getUpdatedAt())
                .createdAt(course.getCreatedAt())
                .accessPolicyDescription(null)
                .memberCount(null)
                .build();
    }

    public List<CoursePdfFileSummaryResponse> listCoursePdfFiles(String courseId) {
        return courseMaterialsService.listByCourseId(courseId).stream()
                .map(CoursePdfFileSummaryResponse::fromEntity)
                .toList();
    }

    public List<KnowledgeBaseSummaryResponse> listKnowledgeBases(String courseId) {
        return knowledgeBasesService.listByCourseId(courseId).stream()
                .map(KnowledgeBaseSummaryResponse::fromEntity)
                .toList();
    }

    private CourseSummaryResponse buildSummary(
            Courses course,
            List<CourseMaterials> materials,
            List<KnowledgeBases> knowledgeBases
    ) {
        Optional<IndexRuns> latestIndexRun = knowledgeBases.stream()
                .flatMap(knowledgeBase -> indexRunsService.listByKnowledgeBaseId(knowledgeBase.getId()).stream())
                .max(Comparator
                        .comparing(IndexRuns::getCreatedAt, Comparator.nullsFirst(LocalDateTime::compareTo))
                        .thenComparing(IndexRuns::getId, Comparator.nullsLast(Long::compareTo)));

        return CourseSummaryResponse.builder()
                .id(course.getId())
                .courseId(course.getCourseId())
                .courseName(course.getCourseName())
                .description(course.getDescription())
                .status(course.getStatus())
                .accessPolicy(course.getAccessPolicy())
                .materialCount((long) materials.size())
                .parsedMaterialCount(countByStatus(materials, "done"))
                .failedMaterialCount(countByStatus(materials, "failed"))
                .knowledgeBaseCount((long) knowledgeBases.size())
                .activeKnowledgeBaseCount(knowledgeBases.stream()
                        .filter(knowledgeBase -> "active".equalsIgnoreCase(knowledgeBase.getStatus()))
                        .count())
                .latestIndexRunId(latestIndexRun.map(IndexRuns::getId).orElse(null))
                .latestIndexRunStatus(latestIndexRun.map(IndexRuns::getStatus).orElse(null))
                .updatedAt(course.getUpdatedAt())
                .build();
    }

    private boolean matchesStatus(Courses course, String status) {
        return !StringUtils.hasText(status) || Objects.equals(course.getStatus(), status);
    }

    private boolean matchesKeyword(Courses course, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
        return contains(course.getCourseId(), normalizedKeyword)
                || contains(course.getCourseName(), normalizedKeyword)
                || contains(course.getDescription(), normalizedKeyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private long countByStatus(List<CourseMaterials> materials, String status) {
        return materials.stream()
                .filter(material -> status.equalsIgnoreCase(material.getParseStatus()))
                .count();
    }
}
