package org.ysu.ckqaback.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ysu.ckqaback.entity.IndexArtifacts;
import org.ysu.ckqaback.entity.IndexRuns;
import org.ysu.ckqaback.service.IndexArtifactsService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndexArtifactRegistryServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRegisterLancedbAndProcessLogArtifacts() throws Exception {
        Files.createDirectories(tempDir.resolve("index/output/lancedb"));
        Files.createDirectories(tempDir.resolve("index/logs"));
        Files.writeString(tempDir.resolve("index/logs/process.log"), "ok");

        IndexRuns run = new IndexRuns();
        run.setId(18L);

        IndexArtifactsService artifactsService = mock(IndexArtifactsService.class);
        when(artifactsService.saveBatch(org.mockito.ArgumentMatchers.anyList())).thenReturn(true);
        IndexArtifactRegistryService registry = new IndexArtifactRegistryService(artifactsService);

        List<IndexArtifacts> artifacts = registry.scanAndRegister(run, tempDir, "user_2/kb_5/build_27");

        verify(artifactsService).removeByIndexRunId(18L);
        assertThat(artifacts).extracting(IndexArtifacts::getArtifactType)
                .contains("lancedb", "log", "output_dir");
        assertThat(artifacts).allSatisfy(artifact -> {
            assertThat(artifact.getStorageUri()).startsWith("user_2/kb_5/build_27/");
            assertThat(artifact.getStorageUri()).doesNotContain(tempDir.toString());
            assertThat(artifact.getStorageScope()).isEqualTo("local");
        });
        assertThat(artifacts).filteredOn(artifact -> "lancedb".equals(artifact.getArtifactType()))
                .extracting(IndexArtifacts::getArtifactStatus)
                .containsExactly("ready");
    }
}
