package org.ysu.ckqaback.course;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.ysu.ckqaback.cache.StudentCacheKeyFactory;
import org.ysu.ckqaback.cache.StudentRedisCacheService;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.course.dto.CourseQueryRequest;
import org.ysu.ckqaback.course.dto.CourseSummaryResponse;
import org.ysu.ckqaback.entity.CourseMemberships;
import org.ysu.ckqaback.entity.CourseMaterials;
import org.ysu.ckqaback.entity.Courses;
import org.ysu.ckqaback.entity.IndexRuns;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.entity.Users;
import org.ysu.ckqaback.service.CourseMembershipsService;
import org.ysu.ckqaback.service.CourseMaterialsService;
import org.ysu.ckqaback.service.CoursesService;
import org.ysu.ckqaback.service.IndexRunsService;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import org.ysu.ckqaback.service.UsersService;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseLookupServiceTest {

    private CoursesService coursesService;
    private CourseMaterialsService courseMaterialsService;
    private KnowledgeBasesService knowledgeBasesService;
    private IndexRunsService indexRunsService;
    private CourseMembershipsService courseMembershipsService;
    private UsersService usersService;
    private CourseAccessService courseAccessService;
    private CourseLookupService service;

    @BeforeEach
    void setUp() {
        coursesService = mock(CoursesService.class);
        courseMaterialsService = mock(CourseMaterialsService.class);
        knowledgeBasesService = mock(KnowledgeBasesService.class);
        indexRunsService = mock(IndexRunsService.class);
        courseMembershipsService = mock(CourseMembershipsService.class);
        usersService = mock(UsersService.class);
        courseAccessService = mock(CourseAccessService.class);
        when(courseAccessService.canReadCourse(any(Courses.class), any())).thenReturn(true);
        service = new CourseLookupService(
                coursesService,
                courseMaterialsService,
                knowledgeBasesService,
                indexRunsService,
                courseMembershipsService,
                usersService,
                courseAccessService
        );
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldUseRedisCacheForAuthenticatedCourseList() {
        StudentRedisCacheService cacheService = mock(StudentRedisCacheService.class);
        StudentCacheKeyFactory keyFactory = mock(StudentCacheKeyFactory.class);
        service.setStudentRedisCacheService(cacheService);
        service.setStudentCacheKeyFactory(keyFactory);
        CourseQueryRequest request = new CourseQueryRequest();
        ApiPageData<CourseSummaryResponse> cachedPage = new ApiPageData<>(
                List.of(CourseSummaryResponse.builder()
                        .id(1L)
                        .courseId("os")
                        .courseName("操作系统")
                        .status("active")
                        .build()),
                1,
                20,
                1,
                1
        );
        when(keyFactory.coursesKey("STU2026001", request)).thenReturn("courses-key");
        when(cacheService.getOrLoad(eq("courses-key"), any(com.fasterxml.jackson.core.type.TypeReference.class), any(), any()))
                .thenReturn((ApiPageData) cachedPage);

        ApiPageData<CourseSummaryResponse> page = service.listCourses(request, "STU2026001");

        assertThat(page).isSameAs(cachedPage);
        verify(coursesService, never()).list();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldValidateCourseReadableBeforeUsingKnowledgeBaseCache() {
        StudentRedisCacheService cacheService = mock(StudentRedisCacheService.class);
        StudentCacheKeyFactory keyFactory = mock(StudentCacheKeyFactory.class);
        service.setStudentRedisCacheService(cacheService);
        service.setStudentCacheKeyFactory(keyFactory);
        when(keyFactory.courseKnowledgeBasesKey("STU2026001", "os")).thenReturn("course-kbs-key");
        List<org.ysu.ckqaback.course.dto.KnowledgeBaseSummaryResponse> cached = List.of(
                org.ysu.ckqaback.course.dto.KnowledgeBaseSummaryResponse.of(3L, "kb-os", "操作系统知识库", "active", 17L)
        );
        when(cacheService.getOrLoad(eq("course-kbs-key"), any(com.fasterxml.jackson.core.type.TypeReference.class), any(), any()))
                .thenReturn((List) cached);

        List<org.ysu.ckqaback.course.dto.KnowledgeBaseSummaryResponse> responses = service.listKnowledgeBases("os", "STU2026001");

        assertThat(responses).hasSize(1);
        verify(courseAccessService).assertCourseReadable("os", "STU2026001");
        verify(knowledgeBasesService, never()).listByCourseId("os");
    }

    @Test
    void shouldPageCoursesAndAggregateCounts() {
        Courses os = course(1L, "os", "操作系统", "active", LocalDateTime.of(2026, 4, 28, 9, 30));
        Courses ds = course(2L, "ds", "数据结构", "archived", LocalDateTime.of(2026, 4, 27, 9, 30));
        KnowledgeBases osKb = knowledgeBase(10L, "os", "active");
        KnowledgeBases osDraftKb = knowledgeBase(11L, "os", "draft");
        when(coursesService.list()).thenReturn(List.of(ds, os));
        when(courseMaterialsService.listByCourseId("os")).thenReturn(List.of(
                material("done"),
                material("failed"),
                material("pending")
        ));
        when(courseMaterialsService.listByCourseId("ds")).thenReturn(List.of());
        when(knowledgeBasesService.listByCourseId("os")).thenReturn(List.of(osKb, osDraftKb));
        when(knowledgeBasesService.listByCourseId("ds")).thenReturn(List.of());
        when(courseMembershipsService.listActiveTeachersByCourseIds(anyCollection())).thenReturn(List.of(
                teacherMembership(21L, 8L, "os", "active")
        ));
        Users teacherUser = user(8L, "t008", "zhang", "张老师");
        teacherUser.setAvatarObjectKey("user-avatars/user-avatar-t008.png");
        teacherUser.setExtraMetadata("{\"department\":\"计算机学院\",\"title\":\"副教授\",\"employee_no\":\"T008\"}");
        when(usersService.listByIds(anyCollection())).thenReturn(List.of(teacherUser));
        when(indexRunsService.listByKnowledgeBaseId(10L)).thenReturn(List.of(
                indexRun(7L, "success", LocalDateTime.of(2026, 4, 28, 10, 0))
        ));
        when(indexRunsService.listByKnowledgeBaseId(11L)).thenReturn(List.of(
                indexRun(5L, "failed", LocalDateTime.of(2026, 4, 27, 10, 0))
        ));

        CourseQueryRequest request = new CourseQueryRequest();
        request.setPage(1);
        request.setSize(1);

        ApiPageData<CourseSummaryResponse> page = service.listCourses(request);

        assertThat(page.getItems()).hasSize(1);
        CourseSummaryResponse summary = page.getItems().getFirst();
        assertThat(summary.getCourseId()).isEqualTo("os");
        assertThat(summary.getCoverUrl()).isEqualTo(CourseCoverService.DEFAULT_COURSE_COVER_URL);
        assertThat(summary.getMaterialCount()).isEqualTo(3L);
        assertThat(summary.getParsedMaterialCount()).isEqualTo(1L);
        assertThat(summary.getFailedMaterialCount()).isEqualTo(1L);
        assertThat(summary.getKnowledgeBaseCount()).isEqualTo(2L);
        assertThat(summary.getActiveKnowledgeBaseCount()).isEqualTo(1L);
        assertThat(summary.getLatestIndexRunId()).isEqualTo(7L);
        assertThat(summary.getTeacherCount()).isEqualTo(1L);
        assertThat(summary.getTeachers()).singleElement()
                .satisfies(teacher -> {
                    assertThat(teacher.getUserId()).isEqualTo(8L);
                    assertThat(teacher.getUserCode()).isEqualTo("t008");
                    assertThat(teacher.getDisplayName()).isEqualTo("张老师");
                    assertThat(teacher.getAvatarUrl()).isEqualTo("/api/v1/user-avatars/user-avatar-t008.png");
                    assertThat(teacher.getDepartment()).isEqualTo("计算机学院");
                    assertThat(teacher.getTitle()).isEqualTo("副教授");
                    assertThat(teacher.getEmployeeNo()).isEqualTo("T008");
                });
        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getPages()).isEqualTo(1L);

        ArgumentCaptor<Collection<String>> courseIdsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(courseMembershipsService).listActiveTeachersByCourseIds(courseIdsCaptor.capture());
        assertThat(courseIdsCaptor.getValue()).containsExactly("os");
    }

    @Test
    void shouldHideArchivedCoursesByDefaultAndReturnThemWhenStatusIsSelected() {
        Courses os = course(1L, "os", "操作系统", "active", LocalDateTime.of(2026, 4, 28, 9, 30));
        Courses ds = course(2L, "ds", "数据结构", "archived", LocalDateTime.of(2026, 4, 27, 9, 30));
        Courses db = course(3L, "db", "数据库", "inactive", LocalDateTime.of(2026, 4, 26, 9, 30));
        when(coursesService.list()).thenReturn(List.of(ds, os, db));
        when(courseMaterialsService.listByCourseId(any())).thenReturn(List.of());
        when(knowledgeBasesService.listByCourseId(any())).thenReturn(List.of());
        when(courseMembershipsService.listActiveTeachersByCourseIds(anyCollection())).thenReturn(List.of());

        CourseQueryRequest defaultRequest = new CourseQueryRequest();
        assertThat(service.listCourses(defaultRequest).getItems())
                .extracting(CourseSummaryResponse::getCourseId)
                .containsExactly("os");

        CourseQueryRequest archivedRequest = new CourseQueryRequest();
        archivedRequest.setStatus("archived");
        assertThat(service.listCourses(archivedRequest).getItems())
                .extracting(CourseSummaryResponse::getCourseId)
                .containsExactly("ds");

        CourseQueryRequest allRequest = new CourseQueryRequest();
        allRequest.setStatus("all");
        assertThat(service.listCourses(allRequest).getItems())
                .extracting(CourseSummaryResponse::getCourseId)
                .containsExactly("os", "ds", "db");
    }

    @Test
    void shouldReturnEmptyPageWhenOffsetIsBeyondTotal() {
        when(coursesService.list()).thenReturn(List.of(
                course(1L, "os", "操作系统", "active", LocalDateTime.of(2026, 4, 28, 9, 30))
        ));
        CourseQueryRequest request = new CourseQueryRequest();
        request.setPage(Integer.MAX_VALUE);
        request.setSize(100);

        ApiPageData<CourseSummaryResponse> page = service.listCourses(request);

        assertThat(page.getItems()).isEmpty();
        verify(courseMembershipsService, never()).listActiveTeachersByCourseIds(anyCollection());
    }

    @Test
    void shouldNotLetNullCreatedAtWinLatestIndexRun() {
        Courses os = course(1L, "os", "操作系统", "active", LocalDateTime.of(2026, 4, 28, 9, 30));
        KnowledgeBases kb = knowledgeBase(10L, "os", "active");
        when(coursesService.list()).thenReturn(List.of(os));
        when(courseMaterialsService.listByCourseId("os")).thenReturn(List.of());
        when(knowledgeBasesService.listByCourseId("os")).thenReturn(List.of(kb));
        when(courseMembershipsService.listActiveTeachersByCourseIds(anyCollection())).thenReturn(List.of());
        when(indexRunsService.listByKnowledgeBaseId(10L)).thenReturn(List.of(
                indexRun(100L, "running", null),
                indexRun(7L, "success", LocalDateTime.of(2026, 4, 28, 10, 0))
        ));

        CourseSummaryResponse summary = service.listCourses(new CourseQueryRequest()).getItems().getFirst();

        assertThat(summary.getLatestIndexRunId()).isEqualTo(7L);
        assertThat(summary.getLatestIndexRunStatus()).isEqualTo("success");
    }

    @Test
    void shouldHandleEmptyMaterialsAndKnowledgeBases() {
        Courses os = course(1L, "os", "操作系统", "active", LocalDateTime.of(2026, 4, 28, 9, 30));
        os.setCoverUrl("/api/v1/course-covers/os.png");
        when(coursesService.list()).thenReturn(List.of(os));
        when(courseMaterialsService.listByCourseId("os")).thenReturn(List.of());
        when(knowledgeBasesService.listByCourseId("os")).thenReturn(List.of());
        when(courseMembershipsService.listActiveTeachersByCourseIds(anyCollection())).thenReturn(List.of());

        CourseSummaryResponse summary = service.listCourses(new CourseQueryRequest()).getItems().getFirst();

        assertThat(summary.getCoverUrl()).isEqualTo("/api/v1/course-covers/os.png");
        assertThat(summary.getMaterialCount()).isZero();
        assertThat(summary.getParsedMaterialCount()).isZero();
        assertThat(summary.getFailedMaterialCount()).isZero();
        assertThat(summary.getKnowledgeBaseCount()).isZero();
        assertThat(summary.getActiveKnowledgeBaseCount()).isZero();
        assertThat(summary.getLatestIndexRunId()).isNull();
        assertThat(summary.getLatestIndexRunStatus()).isNull();
        assertThat(summary.getTeachers()).isEmpty();
        assertThat(summary.getTeacherCount()).isZero();
    }

    @Test
    void shouldFilterInactiveTeacherMembershipsWhenAggregatingTeachers() {
        Courses os = course(1L, "os", "操作系统", "active", LocalDateTime.of(2026, 4, 28, 9, 30));
        when(coursesService.list()).thenReturn(List.of(os));
        when(courseMaterialsService.listByCourseId("os")).thenReturn(List.of());
        when(knowledgeBasesService.listByCourseId("os")).thenReturn(List.of());
        when(courseMembershipsService.listActiveTeachersByCourseIds(anyCollection())).thenReturn(List.of(
                teacherMembership(21L, 8L, "os", "inactive"),
                teacherMembership(22L, 9L, "os", "disabled"),
                studentMembership(23L, 10L, "os", "active")
        ));

        CourseSummaryResponse summary = service.listCourses(new CourseQueryRequest()).getItems().getFirst();

        assertThat(summary.getTeachers()).isEmpty();
        assertThat(summary.getTeacherCount()).isZero();
    }

    @Test
    void shouldReturnTeachersInStableOrderWhenCourseHasMultipleActiveTeachers() {
        Courses os = course(1L, "os", "操作系统", "active", LocalDateTime.of(2026, 4, 28, 9, 30));
        when(coursesService.list()).thenReturn(List.of(os));
        when(courseMaterialsService.listByCourseId("os")).thenReturn(List.of());
        when(knowledgeBasesService.listByCourseId("os")).thenReturn(List.of());
        when(courseMembershipsService.listActiveTeachersByCourseIds(anyCollection())).thenReturn(List.of(
                teacherMembership(30L, 9L, "os", "active"),
                teacherMembership(20L, 8L, "os", "active")
        ));
        when(usersService.listByIds(anyCollection())).thenReturn(List.of(
                user(8L, "t008", "zhang", "张老师"),
                user(9L, "t009", "li", "李老师")
        ));

        CourseSummaryResponse summary = service.listCourses(new CourseQueryRequest()).getItems().getFirst();

        assertThat(summary.getTeacherCount()).isEqualTo(2L);
        assertThat(summary.getTeachers())
                .extracting("userId")
                .containsExactly(8L, 9L);
    }

    @Test
    void shouldIgnoreMissingAndInactiveTeacherUsersWhenAggregatingTeachers() {
        Courses os = course(1L, "os", "操作系统", "active", LocalDateTime.of(2026, 4, 28, 9, 30));
        when(coursesService.list()).thenReturn(List.of(os));
        when(courseMaterialsService.listByCourseId("os")).thenReturn(List.of());
        when(knowledgeBasesService.listByCourseId("os")).thenReturn(List.of());
        when(courseMembershipsService.listActiveTeachersByCourseIds(anyCollection())).thenReturn(List.of(
                teacherMembership(20L, 8L, "os", "active"),
                teacherMembership(21L, 9L, "os", "active"),
                teacherMembership(22L, 10L, "os", "active")
        ));
        Users disabledUser = user(9L, "t009", "li", "李老师");
        disabledUser.setStatus("disabled");
        when(usersService.listByIds(anyCollection())).thenReturn(List.of(
                user(8L, "t008", "zhang", "张老师"),
                disabledUser
        ));

        CourseSummaryResponse summary = service.listCourses(new CourseQueryRequest()).getItems().getFirst();

        assertThat(summary.getTeacherCount()).isEqualTo(1L);
        assertThat(summary.getTeachers())
                .extracting("userId")
                .containsExactly(8L);
    }

    private Courses course(Long id, String courseId, String courseName, String status, LocalDateTime updatedAt) {
        Courses course = new Courses();
        course.setId(id);
        course.setCourseId(courseId);
        course.setCourseName(courseName);
        course.setStatus(status);
        course.setUpdatedAt(updatedAt);
        return course;
    }

    private CourseMaterials material(String status) {
        CourseMaterials material = new CourseMaterials();
        material.setParseStatus(status);
        return material;
    }

    private CourseMemberships teacherMembership(Long id, Long userId, String courseId, String status) {
        return membership(id, userId, courseId, "teacher", status);
    }

    private CourseMemberships studentMembership(Long id, Long userId, String courseId, String status) {
        return membership(id, userId, courseId, "student", status);
    }

    private CourseMemberships membership(Long id, Long userId, String courseId, String role, String status) {
        CourseMemberships membership = new CourseMemberships();
        membership.setId(id);
        membership.setUserId(userId);
        membership.setCourseId(courseId);
        membership.setMembershipRole(role);
        membership.setStatus(status);
        return membership;
    }

    private Users user(Long id, String userCode, String username, String displayName) {
        Users user = new Users();
        user.setId(id);
        user.setUserCode(userCode);
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setStatus("active");
        return user;
    }

    private KnowledgeBases knowledgeBase(Long id, String courseId, String status) {
        KnowledgeBases knowledgeBase = new KnowledgeBases();
        knowledgeBase.setId(id);
        knowledgeBase.setCourseId(courseId);
        knowledgeBase.setStatus(status);
        return knowledgeBase;
    }

    private IndexRuns indexRun(Long id, String status, LocalDateTime createdAt) {
        IndexRuns indexRun = new IndexRuns();
        indexRun.setId(id);
        indexRun.setStatus(status);
        indexRun.setCreatedAt(createdAt);
        return indexRun;
    }
}
