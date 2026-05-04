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
import org.ysu.ckqaback.course.dto.CourseTeacherResponse;
import org.ysu.ckqaback.entity.CourseMemberships;
import org.ysu.ckqaback.entity.CourseMaterials;
import org.ysu.ckqaback.entity.Courses;
import org.ysu.ckqaback.entity.IndexRuns;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.entity.Users;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.course.dto.KnowledgeBaseSummaryResponse;
import org.ysu.ckqaback.service.CourseMaterialsService;
import org.ysu.ckqaback.service.CourseMembershipsService;
import org.ysu.ckqaback.service.CoursesService;
import org.ysu.ckqaback.service.IndexRunsService;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import org.ysu.ckqaback.service.UsersService;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final CourseMembershipsService courseMembershipsService;
    private final UsersService usersService;

    public ApiPageData<CourseSummaryResponse> listCourses(CourseQueryRequest request) {
        long page = request.getPage() == null ? 1L : Math.max(1L, request.getPage());
        long size = request.getSize() == null ? 20L : Math.max(1L, request.getSize());

        List<Courses> filtered = coursesService.list().stream()
                .filter(course -> matchesStatus(course, request.getStatus()))
                .filter(course -> matchesKeyword(course, request.getKeyword()))
                .sorted(Comparator.comparing(Courses::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        long total = filtered.size();
        long offset = (page - 1L) * size;
        long pages = total == 0 ? 0 : (long) Math.ceil((double) total / size);
        if (offset >= total) {
            return new ApiPageData<>(List.of(), page, size, total, pages);
        }

        int fromIndex = (int) offset;
        int toIndex = (int) Math.min(offset + size, total);
        List<Courses> pageCourses = filtered.subList(fromIndex, toIndex);
        Map<String, List<CourseTeacherResponse>> teachersByCourseId = buildTeachersByCourseIds(
                pageCourses.stream().map(Courses::getCourseId).toList()
        );
        List<CourseSummaryResponse> items = pageCourses.stream()
                .map(course -> buildSummary(
                        course,
                        courseMaterialsService.listByCourseId(course.getCourseId()),
                        knowledgeBasesService.listByCourseId(course.getCourseId()),
                        teachersByCourseId.getOrDefault(course.getCourseId(), List.of())
                ))
                .toList();

        return new ApiPageData<>(items, page, size, total, pages);
    }

    public CourseDetailResponse getCourseDetail(String courseId) {
        Courses course = coursesService.list().stream()
                .filter(item -> Objects.equals(item.getCourseId(), courseId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.NOT_FOUND, "课程不存在"));
        List<CourseMaterials> materials = courseMaterialsService.listByCourseId(course.getCourseId());
        List<KnowledgeBases> knowledgeBases = knowledgeBasesService.listByCourseId(course.getCourseId());
        Map<String, List<CourseTeacherResponse>> teachersByCourseId = buildTeachersByCourseIds(List.of(course.getCourseId()));
        CourseSummaryResponse summary = buildSummary(
                course,
                materials,
                knowledgeBases,
                teachersByCourseId.getOrDefault(course.getCourseId(), List.of())
        );

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
                .teachers(summary.getTeachers())
                .teacherCount(summary.getTeacherCount())
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
            List<KnowledgeBases> knowledgeBases,
            List<CourseTeacherResponse> teachers
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
                .teachers(teachers)
                .teacherCount((long) teachers.size())
                .updatedAt(course.getUpdatedAt())
                .build();
    }

    private Map<String, List<CourseTeacherResponse>> buildTeachersByCourseIds(Collection<String> courseIds) {
        List<String> normalizedCourseIds = courseIds.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (normalizedCourseIds.isEmpty()) {
            return Map.of();
        }

        List<CourseMemberships> memberships = courseMembershipsService.listActiveTeachersByCourseIds(normalizedCourseIds).stream()
                .filter(membership -> "teacher".equalsIgnoreCase(membership.getMembershipRole()))
                .filter(membership -> "active".equalsIgnoreCase(membership.getStatus()))
                .sorted(Comparator
                        .comparing(CourseMemberships::getCourseId, Comparator.nullsLast(String::compareTo))
                        .thenComparing(CourseMemberships::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
        if (memberships.isEmpty()) {
            return Map.of();
        }

        LinkedHashSet<Long> userIds = memberships.stream()
                .map(CourseMemberships::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (userIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Users> activeUsersById = usersService.listByIds(userIds).stream()
                .filter(user -> "active".equalsIgnoreCase(user.getStatus()))
                .collect(Collectors.toMap(Users::getId, Function.identity(), (left, right) -> left));

        return memberships.stream()
                .map(membership -> new CourseTeacherMembership(membership, activeUsersById.get(membership.getUserId())))
                .filter(item -> item.user() != null)
                .collect(Collectors.groupingBy(
                        item -> item.membership().getCourseId(),
                        Collectors.mapping(item -> toTeacherResponse(item.user()), Collectors.toList())
                ));
    }

    private CourseTeacherResponse toTeacherResponse(Users user) {
        return CourseTeacherResponse.builder()
                .userId(user.getId())
                .userCode(user.getUserCode())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .build();
    }

    private record CourseTeacherMembership(CourseMemberships membership, Users user) {
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
