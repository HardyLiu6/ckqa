package org.ysu.ckqaback.index;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.entity.IndexRuns;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.KnowledgeBaseQueryRequest;
import org.ysu.ckqaback.index.dto.KnowledgeBaseSummaryResponse;
import org.ysu.ckqaback.service.IndexRunsService;
import org.ysu.ckqaback.service.KnowledgeBasesService;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeBaseLookupServiceTest {

    private KnowledgeBasesService knowledgeBasesService;
    private IndexRunsService indexRunsService;
    private KnowledgeBaseLookupService service;

    @BeforeEach
    void setUp() {
        knowledgeBasesService = mock(KnowledgeBasesService.class);
        indexRunsService = mock(IndexRunsService.class);
        service = new KnowledgeBaseLookupService(knowledgeBasesService, indexRunsService);
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
