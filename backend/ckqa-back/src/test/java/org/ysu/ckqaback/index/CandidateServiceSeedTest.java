package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;
import org.ysu.ckqaback.entity.PromptTuneRuns;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.CandidateResponse;
import org.ysu.ckqaback.index.dto.PromptTuneRunResponse;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.PromptTuneAuditSamplesService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 4.5 引入 seed 后的 CandidateService 行为单测。
 * 关注点：seed 解析 / baseOverride 决策 / seed-info.json 落盘 / 4109 触发。
 */
class CandidateServiceSeedTest {

    private KnowledgeBaseBuildRunsService buildRunsStore;
    private PromptTuneAuditSamplesService samplesStore;
    private BuildRunWorkspaceService workspaceService;
    private CandidateGenerationOrchestrator orchestrator;
    private AuditWithGoldExporter auditExporter;
    private CandidateManifestReader manifestReader;
    private CandidateMetadataLookup metadataLookup;
    private SeedInfoStore seedInfoStore;
    private PromptTuneService promptTuneService;
    private ObjectMapper objectMapper;
    private CandidateService service;
    private Path workspaceDir;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws Exception {
        buildRunsStore = mock(KnowledgeBaseBuildRunsService.class);
        samplesStore = mock(PromptTuneAuditSamplesService.class);
        workspaceService = mock(BuildRunWorkspaceService.class);
        orchestrator = mock(CandidateGenerationOrchestrator.class);
        auditExporter = mock(AuditWithGoldExporter.class);
        manifestReader = mock(CandidateManifestReader.class);
        metadataLookup = new CandidateMetadataLookup();
        seedInfoStore = mock(SeedInfoStore.class);
        promptTuneService = mock(PromptTuneService.class);
        objectMapper = new ObjectMapper();

        workspaceDir = tmp.resolve("kb-build-runs/user_0/kb_5/build_18");
        Files.createDirectories(workspaceDir.resolve("prompt/candidates"));
        when(workspaceService.resolve(any())).thenReturn(workspaceDir);

        service = new CandidateService(
                buildRunsStore, samplesStore, workspaceService,
                orchestrator, auditExporter, manifestReader, metadataLookup,
                objectMapper, seedInfoStore, promptTuneService
        );

        when(samplesStore.listByBuildRunId(any())).thenReturn(List.of(completedSample()));
        when(manifestReader.read(any())).thenReturn(List.of());
    }

    @Test
    void seedSystemDefaultUsesSystemDefaultOverride() throws Exception {
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRunWithSeed("system_default"));

        try {
            service.generate(18L);
        } catch (Exception ignore) {
            // 后续 manifest reader 抛 4105（空候选），无所谓；测前置 baseOverride
        }

        ArgumentCaptor<CandidateGenerationOrchestrator.BaseOverride> captor =
                ArgumentCaptor.forClass(CandidateGenerationOrchestrator.BaseOverride.class);
        verify(orchestrator).run(any(), any(), captor.capture());
        assertThat(captor.getValue()).isNotNull();
        assertThat(captor.getValue().autoTunedPromptDir().toString()).contains("_disabled_auto_tuned");
    }

    @Test
    void seedGraphragTunedUsesCacheDirWhenReady() throws Exception {
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRunWithSeed("graphrag_tuned"));

        // 单一口径：probeBySelection 报 success
        PromptTuneRunResponse probe = PromptTuneRunResponse.builder()
                .status("success").cacheKey("abc").build();
        when(promptTuneService.probeBySelection(any(), any(), any())).thenReturn(probe);

        PromptTuneRuns hit = new PromptTuneRuns();
        hit.setId(7L);
        hit.setStatus("success");
        hit.setCandidateDir("prompt-tune-cache/abc/run_7");
        when(promptTuneService.findReadyByCacheKey("abc")).thenReturn(Optional.of(hit));
        // 让 candidateDir 解析到带 cache 路径的目录，与默认 workspaceDir 区分
        Path tunedDir = workspaceDir.getParent().getParent().getParent()
                .resolve("prompt-tune-cache/abc/run_7");
        when(workspaceService.resolve(eq("prompt-tune-cache/abc/run_7"))).thenReturn(tunedDir);

        try {
            service.generate(18L);
        } catch (Exception ignore) {}

        ArgumentCaptor<CandidateGenerationOrchestrator.BaseOverride> captor =
                ArgumentCaptor.forClass(CandidateGenerationOrchestrator.BaseOverride.class);
        verify(orchestrator).run(any(), any(), captor.capture());
        assertThat(captor.getValue().autoTunedPromptDir().toString()).contains("prompt-tune-cache/abc/run_7");
    }

    @Test
    void seedGraphragTunedThrows4109WhenProbeNotSuccess() {
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRunWithSeed("graphrag_tuned"));
        when(promptTuneService.probeBySelection(any(), any(), any())).thenReturn(
                PromptTuneRunResponse.builder().status("running").cacheKey("abc").build()
        );

        assertThatThrownBy(() -> service.generate(18L))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(ApiResultCode.SEED_AUTO_TUNED_UNAVAILABLE.getCode()));
    }

    @Test
    void seedGraphragTunedThrows4109WhenProbeSuccessButCacheLookupMisses() {
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRunWithSeed("graphrag_tuned"));
        when(promptTuneService.probeBySelection(any(), any(), any())).thenReturn(
                PromptTuneRunResponse.builder().status("success").cacheKey("abc").build()
        );
        when(promptTuneService.findReadyByCacheKey("abc")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(18L))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(ApiResultCode.SEED_AUTO_TUNED_UNAVAILABLE.getCode()));
    }

    @Test
    void seedNullPassesNullBaseOverrideForBackwardCompat() throws Exception {
        // 关键：null seed 与 system_default 必须区分（决策 2 + 风险 1）
        // null 路径让 orchestrator 收到 null baseOverride，不附加 --auto_tuned_prompt_dir
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRunWithMetadata("{}"));

        try { service.generate(18L); } catch (Exception ignore) {}

        verify(orchestrator).run(any(), any(), eq(null));
    }

    @Test
    void writesSeedInfoAfterScriptRuns() throws Exception {
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRunWithSeed("system_default"));

        try { service.generate(18L); } catch (Exception ignore) {}

        verify(seedInfoStore).write(any(), any());
    }

    @Test
    void seedInfoWriteFailureDoesNotAbortAndStillInjectsSeedIntoResponse() throws Exception {
        // 关键回归：seed-info.json 写盘失败时，POST 响应仍要返回带 seed 的候选列表
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRunWithSeed("system_default"));
        when(manifestReader.read(any())).thenReturn(List.of(
                CandidateResponse.builder().candidateId("default").displayNameZh("默认基线").build()
        ));
        org.mockito.Mockito.doThrow(new IOException("disk full"))
                .when(seedInfoStore).write(any(), any());

        List<CandidateResponse> result = service.generate(18L);

        // 写文件失败被吞，候选列表仍返回，且 seed 字段已注入
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSeed()).isEqualTo("system_default");
    }

    private static PromptTuneAuditSamples completedSample() {
        PromptTuneAuditSamples s = new PromptTuneAuditSamples();
        s.setReviewerDecision("completed");
        return s;
    }

    private static KnowledgeBaseBuildRuns buildRunWithSeed(String seed) {
        return buildRunWithMetadata(
                "{\"customPromptDraft\":{\"seed\":\"" + seed + "\"}}"
        );
    }

    private static KnowledgeBaseBuildRuns buildRunWithMetadata(String json) {
        KnowledgeBaseBuildRuns r = new KnowledgeBaseBuildRuns();
        r.setId(18L);
        r.setKnowledgeBaseId(5L);
        r.setCourseId("crs-1");
        r.setRequestedByUserId(0L);
        r.setWorkspaceUri("user_0/kb_5/build_18");
        r.setBuildMetadata(json);
        r.setSelectedMaterialIds("[101]");
        return r;
    }
}
