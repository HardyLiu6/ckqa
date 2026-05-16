package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.index.dto.PromptTuneRunResponse;
import org.ysu.ckqaback.index.dto.SeedAvailabilityResponse;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeedAvailabilityServiceTest {

    private KnowledgeBaseBuildRunsService buildRunsStore;
    private PromptTuneService promptTuneService;
    private SeedAvailabilityService service;

    @BeforeEach
    void setUp() {
        buildRunsStore = mock(KnowledgeBaseBuildRunsService.class);
        promptTuneService = mock(PromptTuneService.class);
        service = new SeedAvailabilityService(buildRunsStore, promptTuneService, new ObjectMapper());
    }

    @Test
    void systemDefaultAlwaysAvailable() {
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRun("system_default"));
        when(promptTuneService.probeBySelection(any(), any(), any()))
                .thenReturn(PromptTuneRunResponse.notStarted(null, "abc"));

        SeedAvailabilityResponse response = service.evaluate(18L);
        SeedAvailabilityResponse.SeedOption opt = findOption(response, "system_default");
        assertThat(opt.getAvailable()).isTrue();
    }

    @Test
    void graphragTunedAvailableWhenCacheSuccess() {
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRun(null));
        PromptTuneRunResponse hit = PromptTuneRunResponse.builder()
                .status("success")
                .cacheKey("abc")
                .cacheHit(true)
                .build();
        when(promptTuneService.probeBySelection(any(), any(), any())).thenReturn(hit);

        SeedAvailabilityResponse response = service.evaluate(18L);
        SeedAvailabilityResponse.SeedOption opt = findOption(response, "graphrag_tuned");
        assertThat(opt.getAvailable()).isTrue();
    }

    @Test
    void graphragTunedUnavailableWhenCacheNotStarted() {
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRun(null));
        when(promptTuneService.probeBySelection(any(), any(), any()))
                .thenReturn(PromptTuneRunResponse.notStarted(null, "abc"));

        SeedAvailabilityResponse response = service.evaluate(18L);
        SeedAvailabilityResponse.SeedOption opt = findOption(response, "graphrag_tuned");
        assertThat(opt.getAvailable()).isFalse();
        assertThat(opt.getReason()).isEqualTo("auto_tuned_not_started");
    }

    @Test
    void graphragTunedUnavailableWhenCacheRunning() {
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRun(null));
        PromptTuneRunResponse running = PromptTuneRunResponse.builder().status("running").cacheKey("abc").build();
        when(promptTuneService.probeBySelection(any(), any(), any())).thenReturn(running);

        SeedAvailabilityResponse response = service.evaluate(18L);
        SeedAvailabilityResponse.SeedOption opt = findOption(response, "graphrag_tuned");
        assertThat(opt.getAvailable()).isFalse();
        assertThat(opt.getReason()).isEqualTo("auto_tuned_running");
    }

    @Test
    void graphragTunedUnavailableWhenCachePending() {
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRun(null));
        PromptTuneRunResponse pending = PromptTuneRunResponse.builder().status("pending").cacheKey("abc").build();
        when(promptTuneService.probeBySelection(any(), any(), any())).thenReturn(pending);

        SeedAvailabilityResponse response = service.evaluate(18L);
        SeedAvailabilityResponse.SeedOption opt = findOption(response, "graphrag_tuned");
        assertThat(opt.getAvailable()).isFalse();
        assertThat(opt.getReason()).isEqualTo("auto_tuned_pending");
    }

    @Test
    void graphragTunedUnavailableWhenCacheFailed() {
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRun(null));
        PromptTuneRunResponse failed = PromptTuneRunResponse.builder().status("failed").cacheKey("abc").build();
        when(promptTuneService.probeBySelection(any(), any(), any())).thenReturn(failed);

        SeedAvailabilityResponse response = service.evaluate(18L);
        SeedAvailabilityResponse.SeedOption opt = findOption(response, "graphrag_tuned");
        assertThat(opt.getAvailable()).isFalse();
        assertThat(opt.getReason()).isEqualTo("auto_tuned_failed");
    }

    @Test
    void graphragTunedUnavailableWhenProbeThrows() {
        // probeBySelection 抛运行时异常（material id 损坏 / DB 故障 / 序列化失败）
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRun(null));
        when(promptTuneService.probeBySelection(any(), any(), any()))
                .thenThrow(new RuntimeException("DB connection lost"));

        SeedAvailabilityResponse response = service.evaluate(18L);
        SeedAvailabilityResponse.SeedOption opt = findOption(response, "graphrag_tuned");
        assertThat(opt.getAvailable()).isFalse();
        assertThat(opt.getReason()).isEqualTo("evaluation_failed");
    }

    @Test
    void historyDraftAlwaysUnavailableInThisPhase() {
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRun(null));
        when(promptTuneService.probeBySelection(any(), any(), any()))
                .thenReturn(PromptTuneRunResponse.notStarted(null, "abc"));

        SeedAvailabilityResponse response = service.evaluate(18L);
        SeedAvailabilityResponse.SeedOption opt = findOption(response, "history_draft");
        assertThat(opt.getAvailable()).isFalse();
        assertThat(opt.getReason()).isEqualTo("phase_6_not_implemented");
    }

    @Test
    void exposesCurrentSeedFromMetadata() {
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRun("graphrag_tuned"));
        when(promptTuneService.probeBySelection(any(), any(), any()))
                .thenReturn(PromptTuneRunResponse.builder().status("success").cacheKey("abc").build());

        SeedAvailabilityResponse response = service.evaluate(18L);
        assertThat(response.getCurrentSeed()).isEqualTo("graphrag_tuned");
    }

    private static KnowledgeBaseBuildRuns buildRun(String seed) {
        KnowledgeBaseBuildRuns r = new KnowledgeBaseBuildRuns();
        r.setId(18L);
        r.setKnowledgeBaseId(5L);
        r.setCourseId("crs-1");
        r.setSelectedMaterialIds("[101]");
        r.setRequestedByUserId(0L);
        r.setWorkspaceUri("user_0/kb_5/build_18");
        if (seed != null) {
            r.setBuildMetadata("{\"customPromptDraft\":{\"seed\":\"" + seed + "\"}}");
        }
        return r;
    }

    private static SeedAvailabilityResponse.SeedOption findOption(
            SeedAvailabilityResponse response, String key
    ) {
        return response.getOptions().stream()
                .filter(o -> key.equals(o.getKey()))
                .findFirst().orElseThrow();
    }
}
