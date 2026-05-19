package org.ysu.ckqaback.course;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.cache.StudentCacheKeyFactory;
import org.ysu.ckqaback.cache.StudentRedisCacheService;
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
import org.ysu.ckqaback.user.UserAvatarService;

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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP = new TypeReference<>() {
    };
    private static final TypeReference<ApiPageData<CourseSummaryResponse>> COURSE_PAGE_CACHE_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<KnowledgeBaseSummaryResponse>> COURSE_KB_CACHE_TYPE = new TypeReference<>() {
    };

    private final CoursesService coursesService;
    private final CourseMaterialsService courseMaterialsService;
    private final KnowledgeBasesService knowledgeBasesService;
    private final IndexRunsService indexRunsService;
    private final CourseMembershipsService courseMembershipsService;
    private final UsersService usersService;
    private final CourseAccessService courseAccessService;
    private StudentRedisCacheService studentRedisCacheService;
    private StudentCacheKeyFactory studentCacheKeyFactory;

    @Autowired(required = false)
    public void setStudentRedisCacheService(StudentRedisCacheService studentRedisCacheService) {
        this.studentRedisCacheService = studentRedisCacheService;
    }

    @Autowired(required = false)
    public void setStudentCacheKeyFactory(StudentCacheKeyFactory studentCacheKeyFactory) {
        this.studentCacheKeyFactory = studentCacheKeyFactory;
    }

    public ApiPageData<CourseSummaryResponse> listCourses(CourseQueryRequest request) {
        return listCourses(request, null);
    }

    public ApiPageData<CourseSummaryResponse> listCourses(CourseQueryRequest request, String actorUserCode) {
        if (StringUtils.hasText(actorUserCode) && studentRedisCacheService != null && studentCacheKeyFactory != null) {
            String key = studentCacheKeyFactory.coursesKey(actorUserCode, request);
            return studentRedisCacheService.getOrLoad(
                    key,
                    COURSE_PAGE_CACHE_TYPE,
                    studentRedisCacheService.courseTtl(),
                    () -> listCoursesUncached(request, actorUserCode)
            );
        }
        return listCoursesUncached(request, actorUserCode);
    }

    private ApiPageData<CourseSummaryResponse> listCoursesUncached(CourseQueryRequest request, String actorUserCode) {
        long page = request.getPage() == null ? 1L : Math.max(1L, request.getPage());
        long size = request.getSize() == null ? 20L : Math.max(1L, request.getSize());

        List<Courses> filtered = coursesService.list().stream()
                .filter(course -> matchesStatus(course, request.getStatus()))
                .filter(course -> matchesKeyword(course, request.getKeyword()))
                .filter(course -> courseAccessService.canReadCourse(course, actorUserCode))
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
        return getCourseDetail(courseId, null);
    }

    public CourseDetailResponse getCourseDetail(String courseId, String actorUserCode) {
        courseAccessService.assertCourseReadable(courseId, actorUserCode);
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
                .coverUrl(summary.getCoverUrl())
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
        return listKnowledgeBases(courseId, null);
    }

    public List<KnowledgeBaseSummaryResponse> listKnowledgeBases(String courseId, String actorUserCode) {
        if (StringUtils.hasText(actorUserCode)) {
            courseAccessService.assertCourseReadable(courseId, actorUserCode);
        }
        if (StringUtils.hasText(actorUserCode) && studentRedisCacheService != null && studentCacheKeyFactory != null) {
            String key = studentCacheKeyFactory.courseKnowledgeBasesKey(actorUserCode, courseId);
            return studentRedisCacheService.getOrLoad(
                    key,
                    COURSE_KB_CACHE_TYPE,
                    studentRedisCacheService.courseKnowledgeBaseTtl(),
                    () -> listKnowledgeBasesUncached(courseId)
            );
        }
        return listKnowledgeBasesUncached(courseId);
    }

    private List<KnowledgeBaseSummaryResponse> listKnowledgeBasesUncached(String courseId) {
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
                .coverUrl(CourseCoverService.resolveResponseCoverUrl(course.getCoverUrl()))
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
        Map<String, Object> metadata = parseMetadata(user.getExtraMetadata());
        return CourseTeacherResponse.builder()
                .userId(user.getId())
                .userCode(user.getUserCode())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .avatarUrl(UserAvatarService.resolveResponseAvatarUrl(user))
                .department(metadataValue(metadata, "department"))
                .title(metadataValue(metadata, "title"))
                .employeeNo(metadataValue(metadata, "employee_no"))
                .build();
    }

    private Map<String, Object> parseMetadata(String rawMetadata) {
        if (!StringUtils.hasText(rawMetadata)) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(rawMetadata, STRING_OBJECT_MAP);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String metadataValue(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private record CourseTeacherMembership(CourseMemberships membership, Users user) {
    }

    private boolean matchesStatus(Courses course, String status) {
        if (!StringUtils.hasText(status)) {
            return "active".equalsIgnoreCase(course.getStatus());
        }
        if ("all".equalsIgnoreCase(status)) {
            return true;
        }
        return Objects.equals(normalizeStatus(course.getStatus()), normalizeStatus(status));
    }

    private String normalizeStatus(String status) {
        return status == null ? null : status.trim().toLowerCase(Locale.ROOT);
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
