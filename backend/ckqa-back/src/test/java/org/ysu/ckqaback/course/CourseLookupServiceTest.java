package org.ysu.ckqaback.course;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.course.dto.CourseCreateRequest;
import org.ysu.ckqaback.course.dto.CourseQueryRequest;
import org.ysu.ckqaback.course.dto.CourseDetailResponse;
import org.ysu.ckqaback.course.dto.CourseSummaryResponse;
import org.ysu.ckqaback.entity.CourseMaterials;
import org.ysu.ckqaback.entity.Courses;
import org.ysu.ckqaback.entity.IndexRuns;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.service.CourseMaterialsService;
import org.ysu.ckqaback.service.CoursesService;
import org.ysu.ckqaback.service.IndexRunsService;
import org.ysu.ckqaback.service.KnowledgeBasesService;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourseLookupServiceTest {

    private CoursesService coursesService;
    private CourseMaterialsService courseMaterialsService;
    private KnowledgeBasesService knowledgeBasesService;
    private IndexRunsService indexRunsService;
    private CourseLookupService service;

    @BeforeEach
    void setUp() {
        coursesService = mock(CoursesService.class);
        courseMaterialsService = mock(CourseMaterialsService.class);
        knowledgeBasesService = mock(KnowledgeBasesService.class);
        indexRunsService = mock(IndexRunsService.class);
        service = new CourseLookupService(coursesService, courseMaterialsService, knowledgeBasesService, indexRunsService);
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
        assertThat(summary.getMaterialCount()).isEqualTo(3L);
        assertThat(summary.getParsedMaterialCount()).isEqualTo(1L);
        assertThat(summary.getFailedMaterialCount()).isEqualTo(1L);
        assertThat(summary.getKnowledgeBaseCount()).isEqualTo(2L);
        assertThat(summary.getActiveKnowledgeBaseCount()).isEqualTo(1L);
        assertThat(summary.getLatestIndexRunId()).isEqualTo(7L);
        assertThat(page.getTotal()).isEqualTo(2L);
        assertThat(page.getPages()).isEqualTo(2L);
    }

    @Test
    void shouldReturnEmptyPageWhenOffsetIsBeyondTotal() {
        when(coursesService.list()).thenReturn(List.of(
                course(1L, "os", "操作系统", "active", LocalDateTime.of(2026, 4, 28, 9, 30))
        ));
        when(courseMaterialsService.listByCourseId("os")).thenReturn(List.of());
        when(knowledgeBasesService.listByCourseId("os")).thenReturn(List.of());
        CourseQueryRequest request = new CourseQueryRequest();
        request.setPage(Integer.MAX_VALUE);
        request.setSize(100);

        assertThatCode(() -> service.listCourses(request)).doesNotThrowAnyException();
        assertThat(service.listCourses(request).getItems()).isEmpty();
    }

    @Test
    void shouldNotLetNullCreatedAtWinLatestIndexRun() {
        Courses os = course(1L, "os", "操作系统", "active", LocalDateTime.of(2026, 4, 28, 9, 30));
        KnowledgeBases kb = knowledgeBase(10L, "os", "active");
        when(coursesService.list()).thenReturn(List.of(os));
        when(courseMaterialsService.listByCourseId("os")).thenReturn(List.of());
        when(knowledgeBasesService.listByCourseId("os")).thenReturn(List.of(kb));
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
        when(coursesService.list()).thenReturn(List.of(os));
        when(courseMaterialsService.listByCourseId("os")).thenReturn(List.of());
        when(knowledgeBasesService.listByCourseId("os")).thenReturn(List.of());

        CourseSummaryResponse summary = service.listCourses(new CourseQueryRequest()).getItems().getFirst();

        assertThat(summary.getMaterialCount()).isZero();
        assertThat(summary.getParsedMaterialCount()).isZero();
        assertThat(summary.getFailedMaterialCount()).isZero();
        assertThat(summary.getKnowledgeBaseCount()).isZero();
        assertThat(summary.getActiveKnowledgeBaseCount()).isZero();
        assertThat(summary.getLatestIndexRunId()).isNull();
        assertThat(summary.getLatestIndexRunStatus()).isNull();
    }

    @Test
    void shouldCreateCourseWithDefaultsAndReturnDetail() {
        CourseCreateRequest request = new CourseCreateRequest();
        request.setCourseId("db");
        request.setCourseName("数据库系统");
        request.setDescription("数据库课程资料");
        when(coursesService.count(any())).thenReturn(0L);
        when(coursesService.save(any(Courses.class))).thenAnswer(invocation -> {
            Courses saved = invocation.getArgument(0);
            saved.setId(3L);
            return true;
        });

        CourseDetailResponse response = service.createCourse(request);

        assertThat(response.getId()).isEqualTo(3L);
        assertThat(response.getCourseId()).isEqualTo("db");
        assertThat(response.getCourseName()).isEqualTo("数据库系统");
        assertThat(response.getStatus()).isEqualTo("active");
        assertThat(response.getAccessPolicy()).isEqualTo("restricted");
        verify(coursesService).save(any(Courses.class));
    }

    @Test
    void shouldRejectDuplicateCourseIdWhenCreatingCourse() {
        CourseCreateRequest request = new CourseCreateRequest();
        request.setCourseId("os");
        request.setCourseName("操作系统");
        when(coursesService.count(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.createCourse(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("课程ID已存在")
                .extracting("code")
                .isEqualTo(ApiResultCode.COURSE_ID_EXISTS.getCode());
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
