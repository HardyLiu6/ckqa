package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.CandidateResponse;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.PromptTuneAuditSamplesService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandidateServiceTest {

    private KnowledgeBaseBuildRunsService buildRunsStore;
    private PromptTuneAuditSamplesService samplesStore;
    private BuildRunWorkspaceService workspaceService;
    private CandidateGenerationOrchestrator orchestrator;
    private AuditWithGoldExporter auditExporter;
    private CandidateManifestReader manifestReader;
    private CandidateMetadataLookup metadataLookup;
    private CandidateService service;

    @BeforeEach
    void setUp() {
        buildRunsStore = mock(KnowledgeBaseBuildRunsService.class);
        samplesStore = mock(PromptTuneAuditSamplesService.class);
        workspaceService = mock(BuildRunWorkspaceService.class);
        orchestrator = mock(CandidateGenerationOrchestrator.class);
        auditExporter = mock(AuditWithGoldExporter.class);
        manifestReader = mock(CandidateManifestReader.class);
        metadataLookup = new CandidateMetadataLookup();

        service = new CandidateService(
                buildRunsStore,
                samplesStore,
                workspaceService,
                orchestrator,
                auditExporter,
                manifestReader,
                metadataLookup,
                new ObjectMapper()
        );
    }

    @Test
    void generateExportsAuditWithGoldThenCallsScript(@TempDir Path tmp) throws Exception {
        Long buildRunId = 18L;
        KnowledgeBaseBuildRuns buildRun = newBuildRun(buildRunId);
        when(buildRunsStore.getRequiredById(buildRunId)).thenReturn(buildRun);
        when(workspaceService.resolve(any())).thenReturn(tmp);

        PromptTuneAuditSamples sample = new PromptTuneAuditSamples();
        sample.setId(1L);
        sample.setSourceSampleId("sample-001");
        sample.setText("text");
        sample.setReviewerDecision("completed");
        when(samplesStore.listByBuildRunId(buildRunId)).thenReturn(List.of(sample));

        // 写 manifest.json 让契约校验能读到
        Path manifestFile = tmp.resolve("prompt/candidates/manifest.json");
        Files.createDirectories(manifestFile.getParent());
        Files.writeString(manifestFile, """
                {"candidates": [{"candidate_name": "default"}]}
                """);

        // 模拟 reader 读出 1 个候选（与 manifest raw 数量一致 → 不触发契约漂移）
        when(manifestReader.read(any())).thenReturn(List.of(
                CandidateResponse.builder().candidateId("default").build()
        ));

        List<CandidateResponse> result = service.generate(buildRunId);

        // 调用顺序：先导出 audit_with_gold.json，再跑 orchestrator
        // 注意：exporter 收到的是 completed 过滤后的样本（只有 1 条且 reviewerDecision=completed）
        verify(auditExporter).export(any(), any(Path.class));
        verify(orchestrator).run(any(Path.class), any(Path.class));
        verify(manifestReader).read(any(Path.class));
        assertThat(result).hasSize(1);
    }

    @Test
    void generateRejectsWhenNoCompletedSamples() throws Exception {
        // 4104 后端门控：所有样本都是 pending / in_progress 时，拒绝生成
        Long buildRunId = 18L;
        when(buildRunsStore.getRequiredById(buildRunId)).thenReturn(newBuildRun(buildRunId));

        PromptTuneAuditSamples pending = new PromptTuneAuditSamples();
        pending.setId(1L);
        pending.setReviewerDecision("pending");
        PromptTuneAuditSamples inProgress = new PromptTuneAuditSamples();
        inProgress.setId(2L);
        inProgress.setReviewerDecision("in_progress");
        when(samplesStore.listByBuildRunId(buildRunId)).thenReturn(List.of(pending, inProgress));

        assertThatThrownBy(() -> service.generate(buildRunId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ApiResultCode.CANDIDATE_REQUIRES_AUDIT_COMPLETED.getCode());

        // 关键：被拒绝时 orchestrator / exporter 不应被调用
        verify(auditExporter, org.mockito.Mockito.never()).export(any(), any());
        verify(orchestrator, org.mockito.Mockito.never()).run(any(), any());
    }

    @Test
    void generateOnlyExportsCompletedSamplesNotInProgress(@TempDir Path tmp) throws Exception {
        // completed 样本和 in_progress 样本混合时，exporter 只收到 completed 那条
        Long buildRunId = 18L;
        when(buildRunsStore.getRequiredById(buildRunId)).thenReturn(newBuildRun(buildRunId));
        when(workspaceService.resolve(any())).thenReturn(tmp);

        PromptTuneAuditSamples completed = new PromptTuneAuditSamples();
        completed.setId(1L);
        completed.setReviewerDecision("completed");
        PromptTuneAuditSamples inProgress = new PromptTuneAuditSamples();
        inProgress.setId(2L);
        inProgress.setReviewerDecision("in_progress");
        when(samplesStore.listByBuildRunId(buildRunId)).thenReturn(List.of(completed, inProgress));

        Path manifestFile = tmp.resolve("prompt/candidates/manifest.json");
        Files.createDirectories(manifestFile.getParent());
        Files.writeString(manifestFile, "{\"candidates\":[{\"candidate_name\":\"default\"}]}");
        when(manifestReader.read(any())).thenReturn(List.of(
                CandidateResponse.builder().candidateId("default").build()
        ));

        service.generate(buildRunId);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<PromptTuneAuditSamples>> samplesCaptor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(auditExporter).export(samplesCaptor.capture(), any(Path.class));
        List<PromptTuneAuditSamples> exported = samplesCaptor.getValue();
        assertThat(exported).hasSize(1);
        assertThat(exported.get(0).getId()).isEqualTo(1L);
    }

    @Test
    void generateThrowsOnUnknownCandidateInManifest(@TempDir Path tmp) throws Exception {
        // 契约漂移检测：manifest 出现白名单外的候选 → 拒绝接受
        Long buildRunId = 18L;
        when(buildRunsStore.getRequiredById(buildRunId)).thenReturn(newBuildRun(buildRunId));
        when(workspaceService.resolve(any())).thenReturn(tmp);

        PromptTuneAuditSamples completed = new PromptTuneAuditSamples();
        completed.setId(1L);
        completed.setReviewerDecision("completed");
        when(samplesStore.listByBuildRunId(buildRunId)).thenReturn(List.of(completed));

        // 写 manifest 含 2 个候选，但 reader 只识别 1 个（unknown_extra 被跳过）
        Path manifestFile = tmp.resolve("prompt/candidates/manifest.json");
        Files.createDirectories(manifestFile.getParent());
        Files.writeString(manifestFile, """
                {"candidates": [{"candidate_name": "default"}, {"candidate_name": "unknown_extra"}]}
                """);
        when(manifestReader.read(any())).thenReturn(List.of(
                CandidateResponse.builder().candidateId("default").build()
        ));

        assertThatThrownBy(() -> service.generate(buildRunId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ApiResultCode.CANDIDATE_GENERATION_FAILED.getCode());
    }

    @Test
    void listReadsManifestWithoutTriggeringGenerate(@TempDir Path tmp) throws Exception {
        Long buildRunId = 18L;
        KnowledgeBaseBuildRuns buildRun = newBuildRun(buildRunId);
        when(buildRunsStore.getRequiredById(buildRunId)).thenReturn(buildRun);
        when(workspaceService.resolve(any())).thenReturn(tmp);

        when(manifestReader.read(any())).thenReturn(List.of(
                CandidateResponse.builder().candidateId("default").build(),
                CandidateResponse.builder().candidateId("auto_tuned").build()
        ));

        List<CandidateResponse> result = service.list(buildRunId);

        // list 不应触发 export 或 orchestrator
        verify(auditExporter, org.mockito.Mockito.never()).export(any(), any());
        verify(orchestrator, org.mockito.Mockito.never()).run(any(), any());
        assertThat(result).hasSize(2);
    }

    @Test
    void listThrows4105WhenManifestEmpty(@TempDir Path tmp) throws Exception {
        Long buildRunId = 18L;
        when(buildRunsStore.getRequiredById(buildRunId)).thenReturn(newBuildRun(buildRunId));
        when(workspaceService.resolve(any())).thenReturn(tmp);
        when(manifestReader.read(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.list(buildRunId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ApiResultCode.CANDIDATES_NOT_GENERATED.getCode());
    }

    @Test
    void loadPromptTextReturnsFileContent(@TempDir Path tmp) throws Exception {
        Long buildRunId = 18L;
        when(buildRunsStore.getRequiredById(buildRunId)).thenReturn(newBuildRun(buildRunId));
        when(workspaceService.resolve(any())).thenReturn(tmp);

        // 准备 prompt.txt
        Path promptFile = tmp.resolve("prompt/candidates/default/prompt.txt");
        Files.createDirectories(promptFile.getParent());
        Files.writeString(promptFile, "-Goal-\nextract entities\n");

        String text = service.loadPromptText(buildRunId, "default");

        assertThat(text).contains("-Goal-").contains("extract entities");
    }

    @Test
    void loadPromptTextRejectsUnknownCandidateId() {
        when(buildRunsStore.getRequiredById(any())).thenReturn(newBuildRun(18L));

        // 未在 lookup 白名单的 candidateId 必须被拒绝（防路径穿越）
        assertThatThrownBy(() -> service.loadPromptText(18L, "../../etc/passwd"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> service.loadPromptText(18L, "unknown_candidate"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
    }

    @Test
    void loadPromptTextThrowsWhenFileMissing(@TempDir Path tmp) throws Exception {
        Long buildRunId = 18L;
        when(buildRunsStore.getRequiredById(buildRunId)).thenReturn(newBuildRun(buildRunId));
        when(workspaceService.resolve(any())).thenReturn(tmp);

        // 候选 id 合法但文件未生成
        assertThatThrownBy(() -> service.loadPromptText(buildRunId, "default"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ApiResultCode.CANDIDATES_NOT_GENERATED.getCode());
    }

    private KnowledgeBaseBuildRuns newBuildRun(Long id) {
        KnowledgeBaseBuildRuns b = new KnowledgeBaseBuildRuns();
        b.setId(id);
        b.setKnowledgeBaseId(100L);
        b.setWorkspaceUri("user_0/kb_100/build_" + id);
        return b;
    }
}
