package org.ysu.ckqaback.index;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.entity.Courses;
import org.ysu.ckqaback.entity.IndexRuns;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.KnowledgeBaseCreateRequest;
import org.ysu.ckqaback.index.dto.KnowledgeBaseQueryRequest;
import org.ysu.ckqaback.index.dto.KnowledgeBaseSummaryResponse;
import org.ysu.ckqaback.service.CoursesService;
import org.ysu.ckqaback.service.IndexRunsService;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.KnowledgeBasesService;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeBaseLookupServiceTest {

    private KnowledgeBasesService knowledgeBasesService;
    private IndexRunsService indexRunsService;
    private CoursesService coursesService;
    private KnowledgeBaseBuildRunsService buildRunsService;
    private KnowledgeBaseLookupService service;

    @BeforeEach
    void setUp() {
        knowledgeBasesService = mock(KnowledgeBasesService.class);
        indexRunsService = mock(IndexRunsService.class);
        coursesService = mock(CoursesService.class);
        buildRunsService = mock(KnowledgeBaseBuildRunsService.class);
        service = new KnowledgeBaseLookupService(knowledgeBasesService, indexRunsService, coursesService, buildRunsService);
    }

    @Test
    void shouldListKnowledgeBasesAndAttachLatestIndexRun() {
        KnowledgeBases active = knowledgeBase(5L, "os", "os-main", "操作系统主知识库", "active");
        KnowledgeBases draft = knowledgeBase(6L, "math", "math-draft", "高数草稿库", "draft");
        when(knowledgeBasesService.list()).thenReturn(List.of(draft, active));
        when(indexRunsService.listByKnowledgeBaseId(5L)).thenReturn(List.of(
                indexRun(18L, "success", "{\"documents\":12}", LocalDateTime.of(2026, 4, 28, 10, 0)),
                indexRun(17L, "failed", "{}", LocalDateTime.of(2026, 4, 27, 10, 0))
        ));
        when(indexRunsService.listByKnowledgeBaseId(6L)).thenReturn(List.of());

        KnowledgeBaseQueryRequest request = new KnowledgeBaseQueryRequest();
        request.setKeyword("os");
        request.setStatus("active");
        ApiPageData<KnowledgeBaseSummaryResponse> page = service.listKnowledgeBases(request);

        assertThat(page.getItems()).hasSize(1);
        KnowledgeBaseSummaryResponse summary = page.getItems().getFirst();
        assertThat(summary.getKbCode()).isEqualTo("os-main");
        assertThat(summary.getLatestIndexRunId()).isEqualTo(18L);
        assertThat(summary.getLatestIndexRunStatus()).isEqualTo("success");
        assertThat(page.getTotal()).isEqualTo(1L);
    }

    @Test
    void shouldFilterCaseInsensitiveAndOnlyAggregateCurrentPage() {
        KnowledgeBases first = knowledgeBase(5L, "os", "os-main", "操作系统主知识库", "active");
        KnowledgeBases second = knowledgeBase(6L, "db", "db-main", "数据库知识库", "active");
        first.setUpdatedAt(LocalDateTime.of(2026, 4, 28, 10, 0));
        second.setUpdatedAt(LocalDateTime.of(2026, 4, 27, 10, 0));
        when(knowledgeBasesService.list()).thenReturn(List.of(second, first));
        when(indexRunsService.listByKnowledgeBaseId(5L)).thenReturn(List.of(
                indexRun(18L, "success", "{\"documents\":12}", LocalDateTime.of(2026, 4, 28, 10, 0))
        ));

        KnowledgeBaseQueryRequest request = new KnowledgeBaseQueryRequest();
        request.setKeyword("OS");
        request.setStatus("ACTIVE");
        request.setPage(1);
        request.setSize(1);
        ApiPageData<KnowledgeBaseSummaryResponse> page = service.listKnowledgeBases(request);

        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getItems().getFirst().getId()).isEqualTo(5L);
        verify(indexRunsService).listByKnowledgeBaseId(5L);
        verify(indexRunsService, never()).listByKnowledgeBaseId(6L);
    }

    @Test
    void shouldHideArchivedKnowledgeBasesByDefaultAndReturnThemWhenExplicitlyFiltered() {
        KnowledgeBases active = knowledgeBase(5L, "os", "os-main", "操作系统主知识库", "active");
        KnowledgeBases archived = knowledgeBase(6L, "os", "os-old", "操作系统旧知识库", "archived");
        when(knowledgeBasesService.list()).thenReturn(List.of(archived, active));
        when(indexRunsService.listByKnowledgeBaseId(any())).thenReturn(List.of());

        assertThat(service.listKnowledgeBases(new KnowledgeBaseQueryRequest()).getItems())
                .extracting(KnowledgeBaseSummaryResponse::getId)
                .containsExactly(5L);

        KnowledgeBaseQueryRequest archivedRequest = new KnowledgeBaseQueryRequest();
        archivedRequest.setStatus("archived");
        assertThat(service.listKnowledgeBases(archivedRequest).getItems())
                .extracting(KnowledgeBaseSummaryResponse::getId)
                .containsExactly(6L);

        KnowledgeBaseQueryRequest allRequest = new KnowledgeBaseQueryRequest();
        allRequest.setStatus("all");
        assertThat(service.listKnowledgeBases(allRequest).getItems())
                .extracting(KnowledgeBaseSummaryResponse::getId)
                .containsExactlyInAnyOrder(5L, 6L);
    }

    @Test
    void shouldGetKnowledgeBaseDetailWithRunCountsAndLatestMetadata() {
        KnowledgeBases knowledgeBase = knowledgeBase(5L, "os", "os-main", "操作系统主知识库", "active");
        when(knowledgeBasesService.getRequiredById(5L)).thenReturn(knowledgeBase);
        when(indexRunsService.listByKnowledgeBaseId(5L)).thenReturn(List.of(
                indexRun(18L, "success", "{\"documents\":12}", LocalDateTime.of(2026, 4, 28, 10, 0)),
                indexRun(17L, "failed", "{\"error\":\"timeout\"}", LocalDateTime.of(2026, 4, 27, 10, 0))
        ));

        var detail = service.getKnowledgeBase(5L);

        assertThat(detail.getId()).isEqualTo(5L);
        assertThat(detail.getIndexRunCount()).isEqualTo(2L);
        assertThat(detail.getSuccessIndexRunCount()).isEqualTo(1L);
        assertThat(detail.getLatestIndexRunMetadata()).isEqualTo("{\"documents\":12}");
    }

    @Test
    void shouldThrowNotFoundWhenKnowledgeBaseMissing() {
        when(knowledgeBasesService.getRequiredById(404L))
                .thenThrow(new BusinessException(ApiResultCode.KNOWLEDGE_BASE_NOT_FOUND, org.springframework.http.HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.getKnowledgeBase(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("知识库不存在")
                .extracting("code")
                .isEqualTo(ApiResultCode.KNOWLEDGE_BASE_NOT_FOUND.getCode());
    }

    @Test
    void shouldCreateKnowledgeBaseWithDraftDefault() {
        KnowledgeBaseCreateRequest request = new KnowledgeBaseCreateRequest();
        request.setCourseId("os");
        request.setKbCode("os-review");
        request.setName("操作系统复习库");
        request.setDescription("复习资料知识库");
        when(coursesService.getOne(any())).thenReturn(course("os", "active"));
        when(knowledgeBasesService.count(any())).thenReturn(0L);
        when(knowledgeBasesService.save(any(KnowledgeBases.class))).thenAnswer(invocation -> {
            KnowledgeBases saved = invocation.getArgument(0);
            saved.setId(8L);
            return true;
        });
        when(indexRunsService.listByKnowledgeBaseId(8L)).thenReturn(List.of());

        var detail = service.createKnowledgeBase(request);

        assertThat(detail.getId()).isEqualTo(8L);
        assertThat(detail.getCourseId()).isEqualTo("os");
        assertThat(detail.getKbCode()).isEqualTo("os-review");
        assertThat(detail.getStatus()).isEqualTo("draft");
        assertThat(detail.getIndexRunCount()).isZero();
        verify(knowledgeBasesService).save(any(KnowledgeBases.class));
    }

    @Test
    void shouldCreateKnowledgeBaseWithGeneratedCodeWhenCodeIsBlank() {
        KnowledgeBaseCreateRequest request = new KnowledgeBaseCreateRequest();
        request.setCourseId("os");
        request.setName("操作系统复习库");
        request.setDescription("复习资料知识库");
        when(coursesService.getOne(any())).thenReturn(course("os", "active"));
        when(knowledgeBasesService.count(any())).thenReturn(0L);
        when(knowledgeBasesService.save(any(KnowledgeBases.class))).thenAnswer(invocation -> {
            KnowledgeBases saved = invocation.getArgument(0);
            saved.setId(8L);
            return true;
        });

        var detail = service.createKnowledgeBase(request);

        ArgumentCaptor<KnowledgeBases> captor = ArgumentCaptor.forClass(KnowledgeBases.class);
        verify(knowledgeBasesService).save(captor.capture());
        assertThat(captor.getValue().getKbCode()).matches("kb-os-\\d{14}");
        assertThat(detail.getKbCode()).isEqualTo(captor.getValue().getKbCode());
    }

    @Test
    void shouldAppendSuffixWhenGeneratedKnowledgeBaseCodeExists() {
        KnowledgeBaseCreateRequest request = new KnowledgeBaseCreateRequest();
        request.setCourseId("os");
        request.setName("操作系统复习库");
        when(coursesService.getOne(any())).thenReturn(course("os", "active"));
        when(knowledgeBasesService.count(any())).thenReturn(1L, 0L);
        when(knowledgeBasesService.save(any(KnowledgeBases.class))).thenAnswer(invocation -> {
            KnowledgeBases saved = invocation.getArgument(0);
            saved.setId(8L);
            return true;
        });

        service.createKnowledgeBase(request);

        ArgumentCaptor<KnowledgeBases> captor = ArgumentCaptor.forClass(KnowledgeBases.class);
        verify(knowledgeBasesService).save(captor.capture());
        assertThat(captor.getValue().getKbCode()).matches("kb-os-\\d{14}-2");
    }

    @Test
    void shouldUpdateKnowledgeBaseEditableFieldsOnly() {
        KnowledgeBases knowledgeBase = knowledgeBase(8L, "os", "os-review", "操作系统复习库", "draft");
        when(knowledgeBasesService.getRequiredById(8L)).thenReturn(knowledgeBase);
        when(indexRunsService.listByKnowledgeBaseId(8L)).thenReturn(List.of());

        var request = new org.ysu.ckqaback.index.dto.KnowledgeBaseUpdateRequest();
        request.setName("操作系统主知识库");
        request.setDescription("用于正式问答");
        request.setStatus("active");

        var detail = service.updateKnowledgeBase(8L, request);

        assertThat(knowledgeBase.getCourseId()).isEqualTo("os");
        assertThat(knowledgeBase.getKbCode()).isEqualTo("os-review");
        assertThat(knowledgeBase.getName()).isEqualTo("操作系统主知识库");
        assertThat(knowledgeBase.getDescription()).isEqualTo("用于正式问答");
        assertThat(knowledgeBase.getStatus()).isEqualTo("active");
        assertThat(knowledgeBase.getUpdatedAt()).isNotNull();
        assertThat(detail.getName()).isEqualTo("操作系统主知识库");
        verify(knowledgeBasesService).updateById(knowledgeBase);
    }

    @Test
    void shouldDeleteKnowledgeBaseWhenNoBuildRunHistoryExists() {
        KnowledgeBases knowledgeBase = knowledgeBase(8L, "os", "os-review", "操作系统复习库", "draft");
        when(knowledgeBasesService.getRequiredById(8L)).thenReturn(knowledgeBase);
        when(buildRunsService.listByKnowledgeBaseId(8L)).thenReturn(List.of());

        service.deleteKnowledgeBase(8L);

        verify(knowledgeBasesService).removeById(8L);
    }

    @Test
    void shouldRejectDeletingKnowledgeBaseWhenBuildRunHistoryExists() {
        KnowledgeBases knowledgeBase = knowledgeBase(8L, "os", "os-review", "操作系统复习库", "draft");
        when(knowledgeBasesService.getRequiredById(8L)).thenReturn(knowledgeBase);
        when(buildRunsService.listByKnowledgeBaseId(8L)).thenReturn(List.of(new org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns()));

        assertThatThrownBy(() -> service.deleteKnowledgeBase(8L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("知识库已有构建历史，请改为归档保留运行记录")
                .extracting("status")
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
        verify(knowledgeBasesService, never()).removeById(8L);
    }

    @Test
    void shouldRejectKnowledgeBaseCreationWhenCourseMissing() {
        KnowledgeBaseCreateRequest request = new KnowledgeBaseCreateRequest();
        request.setCourseId("missing");
        request.setKbCode("missing-main");
        request.setName("缺失课程知识库");
        when(coursesService.getOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.createKnowledgeBase(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("课程不存在")
                .extracting("code")
                .isEqualTo(ApiResultCode.COURSE_NOT_FOUND.getCode());
        verify(knowledgeBasesService, never()).save(any(KnowledgeBases.class));
    }

    @Test
    void shouldRejectKnowledgeBaseCreationWhenCourseIsArchived() {
        KnowledgeBaseCreateRequest request = new KnowledgeBaseCreateRequest();
        request.setCourseId("os");
        request.setKbCode("os-archived");
        request.setName("归档课程知识库");
        when(coursesService.getOne(any())).thenReturn(course("os", "archived"));

        assertThatThrownBy(() -> service.createKnowledgeBase(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("已归档课程不可编辑，请先撤销归档")
                .extracting("status")
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
        verify(knowledgeBasesService, never()).save(any(KnowledgeBases.class));
    }

    private Courses course(String courseId, String status) {
        Courses course = new Courses();
        course.setCourseId(courseId);
        course.setStatus(status);
        return course;
    }

    private KnowledgeBases knowledgeBase(Long id, String courseId, String kbCode, String name, String status) {
        KnowledgeBases knowledgeBase = new KnowledgeBases();
        knowledgeBase.setId(id);
        knowledgeBase.setCourseId(courseId);
        knowledgeBase.setKbCode(kbCode);
        knowledgeBase.setName(name);
        knowledgeBase.setStatus(status);
        knowledgeBase.setDescription("课程主库");
        knowledgeBase.setActiveIndexRunId(18L);
        knowledgeBase.setCreatedAt(LocalDateTime.of(2026, 4, 28, 9, 0));
        knowledgeBase.setUpdatedAt(LocalDateTime.of(2026, 4, 28, 10, 0));
        return knowledgeBase;
    }

    private IndexRuns indexRun(Long id, String status, String metadata, LocalDateTime createdAt) {
        IndexRuns indexRun = new IndexRuns();
        indexRun.setId(id);
        indexRun.setStatus(status);
        indexRun.setRunMetadata(metadata);
        indexRun.setCreatedAt(createdAt);
        return indexRun;
    }

}
