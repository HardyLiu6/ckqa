package org.ysu.ckqaback.integration.graphrag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.ysu.ckqaback.entity.IndexRuns;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.integration.process.ProcessContext;
import org.ysu.ckqaback.integration.process.ProcessExecutionResult;
import org.ysu.ckqaback.integration.process.ProcessRunner;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class GraphRagIndexOrchestratorTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRunIndexWithBuildRunScopedDirectoriesAndProcessLog() throws Exception {
        ProcessRunner processRunner = mock(ProcessRunner.class);
        CkqaIntegrationProperties properties = properties();
        GraphRagIndexOrchestrator orchestrator = new GraphRagIndexOrchestrator(properties, processRunner);
        IndexRuns run = indexRun();
        Path workspace = tempDir.resolve("runtime/kb-build-runs/user_0/kb_5/build_27");

        given(processRunner.run(any(), any(), any(), any(), any())).willReturn(success());

        orchestrator.runIndex(run, workspace);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> envCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<ProcessContext> contextCaptor = ArgumentCaptor.forClass(ProcessContext.class);
        then(processRunner).should().run(
                eq(List.of("/opt/graphrag/bin/python", "-m", "graphrag", "index", "--root", ".")),
                eq(tempDir.resolve("graphrag")),
                envCaptor.capture(),
                eq(Duration.ofSeconds(1800)),
                contextCaptor.capture()
        );

        Map<String, String> env = envCaptor.getValue();
        Path indexRoot = workspace.resolve("index");
        assertThat(env)
                .containsEntry("GRAPHRAG_INPUT_DIR", indexRoot.resolve("input").toString())
                .containsEntry("GRAPHRAG_OUTPUT_DIR", indexRoot.resolve("output").toString())
                .containsEntry("GRAPHRAG_STORAGE_DIR", indexRoot.resolve("output").toString())
                .containsEntry("GRAPHRAG_REPORTING_DIR", indexRoot.resolve("reports").toString())
                .containsEntry("GRAPHRAG_CACHE_DIR", indexRoot.resolve("cache").toString());
        assertThat(contextCaptor.getValue().getLogFile()).isEqualTo(indexRoot.resolve("logs/process.log"));
    }

    @Test
    void shouldFetchMaterialInputWithSeparateSourceAndOutputFile() throws Exception {
        ProcessRunner processRunner = mock(ProcessRunner.class);
        CkqaIntegrationProperties properties = properties();
        GraphRagIndexOrchestrator orchestrator = new GraphRagIndexOrchestrator(properties, processRunner);
        KnowledgeBases kb = new KnowledgeBases();
        kb.setCourseId("os");
        Path graphInputDir = tempDir.resolve("build/graph-input");

        given(processRunner.run(any(), any(), any(), any(), any())).willReturn(success());

        orchestrator.fetchMaterialInput(indexRun(), kb, 3L, graphInputDir, "section_docs.json", "material_3.section_docs.json");

        then(processRunner).should().run(
                eq(List.of(
                        "/opt/graphrag/bin/python",
                        "utils/fetch_from_minio.py",
                        "os",
                        "--material-id",
                        "3",
                        "--json-file",
                        "section_docs.json",
                        "--input-dir",
                        graphInputDir.toString(),
                        "--output-file",
                        "material_3.section_docs.json"
                )),
                eq(tempDir.resolve("graphrag")),
                eq(Map.of()),
                eq(Duration.ofSeconds(300)),
                any(ProcessContext.class)
        );
    }

    private CkqaIntegrationProperties properties() {
        CkqaIntegrationProperties properties = new CkqaIntegrationProperties();
        properties.getGraphrag().setRoot(tempDir.resolve("graphrag").toString());
        properties.getGraphrag().setPython("/opt/graphrag/bin/python");
        return properties;
    }

    private IndexRuns indexRun() {
        IndexRuns run = new IndexRuns();
        run.setId(18L);
        run.setKnowledgeBaseId(5L);
        return run;
    }

    private ProcessExecutionResult success() {
        return ProcessExecutionResult.builder()
                .command(List.of("ok"))
                .exitCode(0)
                .stdout("ok")
                .stderr("")
                .elapsedSeconds(1L)
                .timedOut(false)
                .terminatedByShutdown(false)
                .build();
    }
}
