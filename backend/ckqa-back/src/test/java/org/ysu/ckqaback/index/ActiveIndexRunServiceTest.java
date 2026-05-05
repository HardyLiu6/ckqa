package org.ysu.ckqaback.index;

import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.IndexArtifacts;
import org.ysu.ckqaback.entity.IndexRuns;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.index.dto.ActiveIndexRunResponse;
import org.ysu.ckqaback.service.IndexArtifactsService;
import org.ysu.ckqaback.service.IndexRunsService;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.KnowledgeBasesService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActiveIndexRunServiceTest {

    @Test
    void shouldActivateSuccessRunWithReadyOutputArtifacts() {
        IndexRuns run = new IndexRuns();
        run.setId(18L);
        run.setKnowledgeBaseId(5L);
        run.setBuildRunId(27L);
        run.setStatus("success");

        IndexRunsService indexRunsService = mock(IndexRunsService.class);
        IndexArtifactsService artifactsService = mock(IndexArtifactsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        KnowledgeBaseBuildRunsService buildRunsService = mock(KnowledgeBaseBuildRunsService.class);
        when(indexRunsService.getRequiredById(18L)).thenReturn(run);
        when(artifactsService.listByIndexRunId(18L)).thenReturn(List.of(
                artifact("output_dir", "ready"),
                artifact("lancedb", "ready")
        ));
        when(buildRunsService.getRequiredById(27L)).thenReturn(new KnowledgeBaseBuildRuns());

        ActiveIndexRunService service = new ActiveIndexRunService(
                indexRunsService,
                artifactsService,
                knowledgeBasesService,
                buildRunsService
        );

        ActiveIndexRunResponse response = service.activate(5L, 18L, true);

        assertThat(response.getKnowledgeBaseId()).isEqualTo(5L);
        assertThat(response.getActiveIndexRunId()).isEqualTo(18L);
        assertThat(response.getBuildRunId()).isEqualTo(27L);
        verify(knowledgeBasesService).updateActiveIndexRunId(5L, 18L);
        verify(buildRunsService).clearActiveIndexRunMarkers(5L);
        verify(buildRunsService).updateById(org.mockito.ArgumentMatchers.argThat(buildRun ->
                buildRun.getActiveIndexRunId().equals(18L)
        ));
    }

    private IndexArtifacts artifact(String type, String status) {
        IndexArtifacts artifact = new IndexArtifacts();
        artifact.setArtifactType(type);
        artifact.setArtifactStatus(status);
        return artifact;
    }
}
