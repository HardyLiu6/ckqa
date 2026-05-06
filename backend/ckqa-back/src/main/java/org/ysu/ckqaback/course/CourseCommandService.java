package org.ysu.ckqaback.course;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.course.dto.CourseCreateRequest;
import org.ysu.ckqaback.course.dto.CourseCoverUploadResponse;
import org.ysu.ckqaback.course.dto.CourseDetailResponse;
import org.ysu.ckqaback.course.dto.CourseTeacherResponse;
import org.ysu.ckqaback.course.dto.CourseUpdateRequest;
import org.ysu.ckqaback.entity.CourseMemberships;
import org.ysu.ckqaback.entity.Courses;
import org.ysu.ckqaback.entity.Users;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.service.CourseMaterialsService;
import org.ysu.ckqaback.service.CourseMembershipsService;
import org.ysu.ckqaback.service.CoursesService;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import org.ysu.ckqaback.service.UsersService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 课程写操作服务。
 */
@Service
@RequiredArgsConstructor
public class CourseCommandService {

    private static final int MAX_COURSE_ID_ATTEMPTS = 5;
    private static final String TEACHER_ROLE = "teacher";
    private static final String ACTIVE = "active";
    private static final String DEFAULT_ACCESS_POLICY = "restricted";
    private static final String MANUAL_ACCESS_SOURCE = "manual";
    private static final String INITIAL_TEACHER_CHANGE_REASON = "COURSE_CREATION_INITIAL_TEACHER";
    // 数据库唯一索引名：courses.uk_course_id。只有确认冲突来自该索引时才重试生成课程 ID。
    private static final String COURSE_ID_UNIQUE_INDEX = "uk_course_id";

    private final CoursesService coursesService;
    private final CourseMembershipsService courseMembershipsService;
    private final CourseMaterialsService courseMaterialsService;
    private final KnowledgeBasesService knowledgeBasesService;
    private final UsersService usersService;
    private final CourseIdGenerator courseIdGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final CourseCoverService courseCoverService;

    @Transactional(propagation = Propagation.REQUIRED)
    public CourseDetailResponse createCourse(CourseCreateRequest request) {
        Users teacher = usersService.getRequiredById(request.getTeacherUserId());
        validateInitialTeacher(teacher);

        for (int attempt = 0; attempt < MAX_COURSE_ID_ATTEMPTS; attempt++) {
            String courseId = courseIdGenerator.generate();
            if (coursesService.count(new LambdaQueryWrapper<Courses>().eq(Courses::getCourseId, courseId)) > 0) {
                continue;
            }

            Courses course = buildCourse(request, courseId);
            try {
                coursesService.save(course);
            } catch (DataIntegrityViolationException ex) {
                if (isCourseIdUniqueConflict(ex)) {
                    continue;
                }
                throw ex;
            }

            CourseMemberships membership = buildInitialTeacherMembership(courseId, teacher.getId(), course.getCreatedAt());
            courseMembershipsService.save(membership);
            eventPublisher.publishEvent(new CourseMembershipAuditEvent(
                    membership.getId(),
                    courseId,
                    teacher.getId(),
                    null
            ));
            return toDetailResponse(course, teacher);
        }

        throw new BusinessException(
                ApiResultCode.COURSE_ID_EXISTS,
                HttpStatus.CONFLICT,
                "课程标识生成冲突，请稍后重试"
        );
    }

    public CourseCoverUploadResponse storeCourseCover(MultipartFile file) {
        return courseCoverService.store(file);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateCourse(String courseId, CourseUpdateRequest request) {
        Courses course = getRequiredCourseByCourseId(courseId);
        course.setCourseName(normalizeText(request.getCourseName()));
        course.setDescription(normalizeNullableText(request.getDescription()));
        course.setStatus(normalizeText(request.getStatus()));
        course.setAccessPolicy(normalizeText(request.getAccessPolicy()));
        course.setUpdatedAt(LocalDateTime.now());
        coursesService.updateById(course);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteCourse(String courseId) {
        Courses course = getRequiredCourseByCourseId(courseId);
        boolean hasMaterials = !courseMaterialsService.listByCourseId(courseId).isEmpty();
        boolean hasKnowledgeBases = !knowledgeBasesService.listByCourseId(courseId).isEmpty();

        if (hasMaterials || hasKnowledgeBases) {
            throw new BusinessException(
                    ApiResultCode.BAD_REQUEST,
                    HttpStatus.CONFLICT,
                    "课程仍有关联资料或知识库，请先清理后删除"
            );
        }

        coursesService.removeById(course.getId());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public CourseCoverUploadResponse updateCourseCover(String courseId, MultipartFile file) {
        Courses course = getRequiredCourseByCourseId(courseId);
        CourseCoverUploadResponse response = courseCoverService.store(file);
        course.setCoverUrl(response.getCoverUrl());
        course.setUpdatedAt(LocalDateTime.now());
        coursesService.updateById(course);
        return response;
    }

    private void validateInitialTeacher(Users teacher) {
        if (!ACTIVE.equalsIgnoreCase(teacher.getStatus())) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "初始教师状态不可用");
        }
        if (!usersService.hasRole(teacher.getId(), TEACHER_ROLE)) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "初始教师必须拥有teacher角色");
        }
    }

    private Courses buildCourse(CourseCreateRequest request, String courseId) {
        LocalDateTime now = LocalDateTime.now();
        Courses course = new Courses();
        course.setIsDeleted(false);
        course.setCourseId(courseId);
        course.setCourseName(normalizeText(request.getCourseName()));
        course.setDescription(normalizeNullableText(request.getDescription()));
        course.setCoverUrl(CourseCoverService.normalizeCoverUrl(request.getCoverUrl()));
        course.setStatus(defaultIfBlank(request.getStatus(), ACTIVE));
        course.setAccessPolicy(defaultIfBlank(request.getAccessPolicy(), DEFAULT_ACCESS_POLICY));
        course.setCreatedAt(now);
        course.setUpdatedAt(now);
        return course;
    }

    private CourseMemberships buildInitialTeacherMembership(String courseId, Long teacherUserId, LocalDateTime now) {
        CourseMemberships membership = new CourseMemberships();
        membership.setCourseId(courseId);
        membership.setUserId(teacherUserId);
        membership.setMembershipRole(TEACHER_ROLE);
        membership.setStatus(ACTIVE);
        membership.setAccessSource(MANUAL_ACCESS_SOURCE);
        membership.setJoinedAt(now);
        membership.setEffectiveFrom(now);
        membership.setChangeReason(INITIAL_TEACHER_CHANGE_REASON);
        membership.setCreatedAt(now);
        membership.setUpdatedAt(now);
        return membership;
    }

    private CourseDetailResponse toDetailResponse(Courses course, Users teacher) {
        CourseTeacherResponse teacherResponse = CourseTeacherResponse.builder()
                .userId(teacher.getId())
                .userCode(teacher.getUserCode())
                .username(teacher.getUsername())
                .displayName(teacher.getDisplayName())
                .build();
        return CourseDetailResponse.builder()
                .id(course.getId())
                .courseId(course.getCourseId())
                .courseName(course.getCourseName())
                .description(course.getDescription())
                .coverUrl(CourseCoverService.resolveResponseCoverUrl(course.getCoverUrl()))
                .status(course.getStatus())
                .accessPolicy(course.getAccessPolicy())
                .materialCount(0L)
                .parsedMaterialCount(0L)
                .failedMaterialCount(0L)
                .knowledgeBaseCount(0L)
                .activeKnowledgeBaseCount(0L)
                .latestIndexRunId(null)
                .latestIndexRunStatus(null)
                .teachers(List.of(teacherResponse))
                .teacherCount(1L)
                .createdAt(course.getCreatedAt())
                .updatedAt(course.getUpdatedAt())
                .accessPolicyDescription(null)
                .memberCount(null)
                .build();
    }

    private Courses getRequiredCourseByCourseId(String courseId) {
        Courses course = coursesService.getOne(new LambdaQueryWrapper<Courses>()
                .eq(Courses::getCourseId, courseId)
                .last("LIMIT 1"));
        if (course == null) {
            throw new BusinessException(ApiResultCode.COURSE_NOT_FOUND, HttpStatus.NOT_FOUND, "课程不存在");
        }
        return course;
    }

    private boolean isCourseIdUniqueConflict(RuntimeException ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(COURSE_ID_UNIQUE_INDEX)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? normalizeText(value) : fallback;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeNullableText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
