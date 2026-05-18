package org.ysu.ckqaback.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.integration.process.ProcessExecutionResult;
import org.ysu.ckqaback.integration.process.ProcessRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExtractionEvalOrchestratorTest {

    private CkqaIntegrationProperties properties;
    private ProcessRunner processRunner;
    private ExtractionEvalOrchestrator orchestrator;

    private void initWithRoot(Path graphragRoot) {
        properties = new CkqaIntegrationProperties();
        properties.getGraphrag().setRoot(graphragRoot.toString());
        properties.getGraphrag().setPython("/usr/bin/python");
        // managedApi.condaEnv 默认值 graphrag-oneapi 已在 properties 默认设置
        processRunner = mock(ProcessRunner.class);
        orchestrator = new ExtractionEvalOrchestrator(properties, processRunner);
    }

    @Test
    void runSingleCandidateExtractInvokesScriptWithExpectedArgs(@TempDir Path graphragRoot) throws Exception {
        initWithRoot(graphragRoot);
        Path samplesFile = graphragRoot.resolve("audit_with_gold.json");
        Files.writeString(samplesFile, "{}");
        Path promptFile = graphragRoot.resolve("prompt.txt");
        Files.writeString(promptFile, "prompt body");
        Path workspace = graphragRoot.resolve("ws");
        Files.createDirectories(workspace);

        ProcessExecutionResult ok = ProcessExecutionResult.builder()
                .exitCode(0).stdout("stdout").stderr("").timedOut(false).build();
        when(processRunner.run(any(), any(), any(), any(), any())).thenReturn(ok);

        orchestrator.runSingleCandidateExtract(
                "default",
                samplesFile,
                promptFile,
                "Concept,Term",
                "eval_18_3",
                workspace
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> argv = ArgumentCaptor.forClass(List.class);
        verify(processRunner).run(argv.capture(), eq(graphragRoot), any(), any(), any());

        // argv 含 -m 模块路径 + 关键参数
        List<String> args = argv.getValue();
        assertThat(args).contains("-m", "scripts.extraction_eval.run_native_extraction");
        assertThat(args).contains("--samples-file", samplesFile.toAbsolutePath().toString());
        assertThat(args).contains("--prompt", promptFile.toAbsolutePath().toString());
        assertThat(args).contains("--candidate-name", "default");
        assertThat(args).contains("--run-id", "eval_18_3");
        assertThat(args).contains("--entity-types", "Concept,Term");
        assertThat(args).contains("--max-gleanings", "1");
        assertThat(args).contains("--concurrency", "1");
        assertThat(args).contains("--overwrite");
    }

    @Test
    void runSingleCandidateExtractRaises5008OnNonZeroExit(@TempDir Path graphragRoot) throws Exception {
        initWithRoot(graphragRoot);
        Path samplesFile = graphragRoot.resolve("audit.json");
        Files.writeString(samplesFile, "{}");
        Path promptFile = graphragRoot.resolve("p.txt");
        Files.writeString(promptFile, "p");
        Path workspace = graphragRoot.resolve("ws");
        Files.createDirectories(workspace);
        when(processRunner.run(any(), any(), any(), any(), any())).thenReturn(
                ProcessExecutionResult.builder()
                        .exitCode(1).stdout("").stderr("boom").timedOut(false).build()
        );

        assertThatThrownBy(() -> orchestrator.runSingleCandidateExtract(
                "default", samplesFile, promptFile, "Concept", "rid", workspace
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("评分抽取失败")
                .hasMessageContaining("boom");
    }

    @Test
    void runScoringInvokesScriptWithExpectedArgs(@TempDir Path graphragRoot) throws Exception {
        initWithRoot(graphragRoot);
        Path workspace = graphragRoot.resolve("ws");
        Files.createDirectories(workspace);
        Path auditFile = graphragRoot.resolve("audit.json");
        Files.writeString(auditFile, "{}");
        when(processRunner.run(any(), any(), any(), any(), any()))
                .thenReturn(ProcessExecutionResult.builder()
                        .exitCode(0).stdout("summary").stderr("").timedOut(false).build());

        orchestrator.runScoring(
                "eval_18_3",
                auditFile,
                workspace
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> argv = ArgumentCaptor.forClass(List.class);
        verify(processRunner).run(argv.capture(), eq(graphragRoot), any(), any(), any());

        List<String> args = argv.getValue();
        assertThat(args).contains("-m", "scripts.extraction_eval.score_extraction_results");
        assertThat(args).contains("--audit", auditFile.toAbsolutePath().toString());
        assertThat(args).contains("--run-id", "eval_18_3");
        assertThat(args).contains("--overwrite");
    }
}
