package org.ysu.ckqaback.index;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.integration.process.ProcessContext;
import org.ysu.ckqaback.integration.process.ProcessExecutionResult;
import org.ysu.ckqaback.integration.process.ProcessRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandidateGenerationOrchestratorSeedTest {

    private CkqaIntegrationProperties properties;
    private ProcessRunner processRunner;
    private CandidateGenerationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        properties = mock(CkqaIntegrationProperties.class);
        CkqaIntegrationProperties.GraphRagProperties graphrag = mock(CkqaIntegrationProperties.GraphRagProperties.class);
        when(properties.getGraphrag()).thenReturn(graphrag);
        when(graphrag.getRoot()).thenReturn("/tmp/graphrag-test");
        when(graphrag.getPython()).thenReturn("python3");
        CkqaIntegrationProperties.ManagedApiProperties managedApi =
                mock(CkqaIntegrationProperties.ManagedApiProperties.class);
        when(graphrag.getManagedApi()).thenReturn(managedApi);
        when(managedApi.getCondaEnv()).thenReturn("graphrag-oneapi");

        processRunner = mock(ProcessRunner.class);
        orchestrator = new CandidateGenerationOrchestrator(properties, processRunner);
    }

    @Test
    void seedSystemDefaultPassesNonExistentAutoTunedDir(@TempDir Path tmp) throws Exception {
        Path audit = tmp.resolve("audit.json");
        Files.writeString(audit, "{}");
        Path output = tmp.resolve("ws/prompt/candidates");
        Files.createDirectories(output);

        when(processRunner.run(any(), any(), any(), any(), any())).thenReturn(
                ProcessExecutionResult.builder()
                        .command(List.of()).exitCode(0).stdout("").stderr("").elapsedSeconds(0L).timedOut(false).build()
        );

        orchestrator.run(audit, output, CandidateGenerationOrchestrator.BaseOverride.systemDefault(output));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(processRunner).run(captor.capture(), any(), any(), any(), any());
        List<String> args = captor.getValue();

        // --auto_tuned_prompt_dir 指向不存在路径，让脚本走 default 分支
        int idx = args.indexOf("--auto_tuned_prompt_dir");
        assertThat(idx).isGreaterThan(-1);
        Path passed = Path.of(args.get(idx + 1));
        assertThat(Files.exists(passed)).isFalse();
        assertThat(passed.toString()).contains("_disabled_auto_tuned");
    }

    @Test
    void seedGraphragTunedPassesProvidedDir(@TempDir Path tmp) throws Exception {
        Path audit = tmp.resolve("audit.json");
        Files.writeString(audit, "{}");
        Path output = tmp.resolve("ws/prompt/candidates");
        Files.createDirectories(output);
        Path autoTunedDir = tmp.resolve("cache/run_3");
        Files.createDirectories(autoTunedDir);
        Files.writeString(autoTunedDir.resolve("extract_graph.txt"), "auto-tuned content");

        when(processRunner.run(any(), any(), any(), any(), any())).thenReturn(
                ProcessExecutionResult.builder()
                        .command(List.of()).exitCode(0).stdout("").stderr("").elapsedSeconds(0L).timedOut(false).build()
        );

        orchestrator.run(audit, output, CandidateGenerationOrchestrator.BaseOverride.graphragTuned(autoTunedDir));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(processRunner).run(captor.capture(), any(), any(), any(), any());
        List<String> args = captor.getValue();
        int idx = args.indexOf("--auto_tuned_prompt_dir");
        assertThat(args.get(idx + 1)).isEqualTo(autoTunedDir.toAbsolutePath().toString());
    }

    @Test
    void noOverrideSkipsAutoTunedDirArg(@TempDir Path tmp) throws Exception {
        // 无 baseOverride（兼容路径）：不传 --auto_tuned_prompt_dir，让脚本用默认值
        Path audit = tmp.resolve("audit.json");
        Files.writeString(audit, "{}");
        Path output = tmp.resolve("ws/prompt/candidates");
        Files.createDirectories(output);

        when(processRunner.run(any(), any(), any(), any(), any())).thenReturn(
                ProcessExecutionResult.builder()
                        .command(List.of()).exitCode(0).stdout("").stderr("").elapsedSeconds(0L).timedOut(false).build()
        );

        orchestrator.run(audit, output, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(processRunner).run(captor.capture(), any(), any(), any(), any());
        List<String> args = captor.getValue();
        assertThat(args).doesNotContain("--auto_tuned_prompt_dir");
    }
}
