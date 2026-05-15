package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.AuditSampleResponse;
import org.ysu.ckqaback.index.dto.AuditSampleUpdateRequest;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import org.ysu.ckqaback.service.PromptTuneAuditSamplesService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuditSampleServiceTest {

    private PromptTuneAuditSamplesService samplesStore;
    private KnowledgeBaseBuildRunsService buildRunsStore;
    private KnowledgeBasesService knowledgeBasesService;
    private AuditPipelineOrchestrator orchestrator;
    private BuildRunWorkspaceService workspaceService;
    private AuditSamplePersistenceService persistence;
    private AuditSampleResponseMapper responseMapper;
    private ObjectMapper objectMapper;

    private AuditSampleService service;

    @BeforeEach
    void setUp() {
        samplesStore = mock(PromptTuneAuditSamplesService.class);
        buildRunsStore = mock(KnowledgeBaseBuildRunsService.class);
        knowledgeBasesService = mock(KnowledgeBasesService.class);
        orchestrator = mock(AuditPipelineOrchestrator.class);
        workspaceService = mock(BuildRunWorkspaceService.class);
        persistence = mock(AuditSamplePersistenceService.class);
        responseMapper = mock(AuditSampleResponseMapper.class);
        objectMapper = new ObjectMapper();

        service = new AuditSampleService(
                samplesStore, buildRunsStore, knowledgeBasesService,
                orchestrator, workspaceService, persistence, responseMapper, objectMapper
        );
    }

    // ─── updateSample 测试 ──────────────────────────────────────────────────

    @Test
    void updateSampleAppliesValueWhenFieldPresent() {
        // 准备样本
        PromptTuneAuditSamples sample = buildSample(100L, 1L);
        sample.setReviewerDecision("pending");
        sample.setReviewerConfidence(null);
        when(samplesStore.getById(100L)).thenReturn(sample);
        when(samplesStore.updateById(any())).thenReturn(true);
        when(buildRunsStore.listByIds(any())).thenReturn(List.of());
        when(responseMapper.toResponse(any(), anyOrNull())).thenReturn(dummyResponse(100L));

        // 构造请求：设置 reviewerDecision 和 reviewerConfidence
        AuditSampleUpdateRequest request = new AuditSampleUpdateRequest();
        request.setReviewerDecision("completed");
        request.setReviewerConfidence(new BigDecimal("0.85"));

        AuditSampleResponse result = service.updateSample(1L, 100L, request);

        assertThat(result).isNotNull();
        // 验证字段被应用
        verify(samplesStore).updateById(argThat(s -> {
            PromptTuneAuditSamples updated = (PromptTuneAuditSamples) s;
            return "completed".equals(updated.getReviewerDecision())
                    && new BigDecimal("0.85").compareTo(updated.getReviewerConfidence()) == 0;
        }));
    }

    @Test
    void updateSampleClearsFieldWhenNullProvidedAndFieldPresent() {
        // 准备样本：skipReason 有值
        PromptTuneAuditSamples sample = buildSample(101L, 2L);
        sample.setSkipReason("暂时跳过");
        sample.setReviewerDecision("skipped");
        when(samplesStore.getById(101L)).thenReturn(sample);
        when(samplesStore.updateById(any())).thenReturn(true);
        when(buildRunsStore.listByIds(any())).thenReturn(List.of());
        when(responseMapper.toResponse(any(), anyOrNull())).thenReturn(dummyResponse(101L));

        // 构造请求：显式传入 skipReason=null（hasField=true）
        AuditSampleUpdateRequest request = new AuditSampleUpdateRequest();
        request.setSkipReason(null); // 调用 setter 会记录 presentFields

        AuditSampleResponse result = service.updateSample(2L, 101L, request);

        assertThat(result).isNotNull();
        // 验证 skipReason 被清空
        verify(samplesStore).updateById(argThat(s -> {
            PromptTuneAuditSamples updated = (PromptTuneAuditSamples) s;
            return updated.getSkipReason() == null;
        }));
    }

    @Test
    void updateSampleLeavesFieldWhenAbsent() {
        // 准备样本：skipReason 和 annotationNotes 有值
        PromptTuneAuditSamples sample = buildSample(102L, 3L);
        sample.setSkipReason("原因A");
        sample.setAnnotationNotes("备注B");
        sample.setReviewerDecision("pending");
        when(samplesStore.getById(102L)).thenReturn(sample);
        when(samplesStore.updateById(any())).thenReturn(true);
        when(buildRunsStore.listByIds(any())).thenReturn(List.of());
        when(responseMapper.toResponse(any(), anyOrNull())).thenReturn(dummyResponse(102L));

        // 构造请求：只设置 reviewerDecision，不设置 skipReason 和 annotationNotes
        AuditSampleUpdateRequest request = new AuditSampleUpdateRequest();
        request.setReviewerDecision("in_progress");

        service.updateSample(3L, 102L, request);

        // 验证 skipReason 和 annotationNotes 保持不变
        verify(samplesStore).updateById(argThat(s -> {
            PromptTuneAuditSamples updated = (PromptTuneAuditSamples) s;
            return "原因A".equals(updated.getSkipReason())
                    && "备注B".equals(updated.getAnnotationNotes());
        }));
    }

    @Test
    void updateSampleRejectsWrongBuildRunId() {
        // 样本属于 buildRunId=5，但请求传入 buildRunId=99
        PromptTuneAuditSamples sample = buildSample(103L, 5L);
        when(samplesStore.getById(103L)).thenReturn(sample);

        AuditSampleUpdateRequest request = new AuditSampleUpdateRequest();
        request.setReviewerDecision("completed");

        assertThatThrownBy(() -> service.updateSample(99L, 103L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException bex = (BusinessException) ex;
                    assertThat(bex.getCode()).isEqualTo(4051); // AUDIT_SAMPLE_NOT_FOUND
                });
    }

    @Test
    void updateSampleThrowsWhenSampleMissing() {
        when(samplesStore.getById(999L)).thenReturn(null);

        AuditSampleUpdateRequest request = new AuditSampleUpdateRequest();
        request.setReviewerDecision("completed");

        assertThatThrownBy(() -> service.updateSample(1L, 999L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException bex = (BusinessException) ex;
                    assertThat(bex.getCode()).isEqualTo(4051); // AUDIT_SAMPLE_NOT_FOUND
                });
    }

    // ─── regenerateAuditSet 测试 ────────────────────────────────────────────

    @Test
    void regenerateAuditSetRejectsWhenAnnotatedSamplesExistAndNotForced() {
        KnowledgeBaseBuildRuns buildRun = buildBuildRun(10L);
        when(buildRunsStore.getRequiredById(10L)).thenReturn(buildRun);
        when(persistence.hasNonPendingSamples(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.regenerateAuditSet(10L, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException bex = (BusinessException) ex;
                    assertThat(bex.getCode()).isEqualTo(4103); // BUILD_RUN_HAS_ANNOTATED_SAMPLES
                });
    }

    // ─── 辅助方法 ───────────────────────────────────────────────────────────

    private PromptTuneAuditSamples buildSample(Long id, Long buildRunId) {
        PromptTuneAuditSamples sample = new PromptTuneAuditSamples();
        sample.setId(id);
        sample.setBuildRunId(buildRunId);
        sample.setKnowledgeBaseId(1L);
        sample.setReviewerDecision("pending");
        sample.setCreatedAt(LocalDateTime.of(2026, 5, 15, 10, 0));
        sample.setUpdatedAt(LocalDateTime.of(2026, 5, 15, 10, 0));
        return sample;
    }

    private KnowledgeBaseBuildRuns buildBuildRun(Long id) {
        KnowledgeBaseBuildRuns run = new KnowledgeBaseBuildRuns();
        run.setId(id);
        run.setKnowledgeBaseId(1L);
        run.setWorkspaceUri("user_1/kb_1/build_" + id);
        run.setSelectedMaterialIds("[1,2,3]");
        run.setCreatedAt(LocalDateTime.of(2026, 5, 15, 9, 0));
        return run;
    }

    private AuditSampleResponse dummyResponse(Long id) {
        return AuditSampleResponse.builder()
                .id(id)
                .buildRunId(1L)
                .reviewerDecision("completed")
                .build();
    }

    private static String anyOrNull() {
        return any();
    }
}
