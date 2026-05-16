package org.ysu.ckqaback.index;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.integration.process.ProcessContext;
import org.ysu.ckqaback.integration.process.ProcessExecutionResult;
import org.ysu.ckqaback.integration.process.ProcessRunner;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandidateGenerationOrchestratorTest {

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
    void invokesPythonScriptWithExpectedArgs() throws Exception {
        Path auditFile = Path.of("/tmp/ws/prompt/candidates/audit_with_gold.json");
        Path outputDir = Path.of("/tmp/ws/prompt/candidates");

        when(processRunner.run(any(), any(), any(), any(), any())).thenReturn(
                ProcessExecutionResult.builder()
                        .command(List.of())
                        .exitCode(0)
                        .stdout("")
                        .stderr("")
                        .elapsedSeconds(0L)
                        .timedOut(false)
                        .build()
        );

        orchestrator.run(auditFile, outputDir, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> argvCaptor = ArgumentCaptor.forClass(List.class);
        verify(processRunner).run(
                argvCaptor.capture(),
                eq(Path.of("/tmp/graphrag-test")),
                eq(Map.of()),
                any(Duration.class),
                any(ProcessContext.class)
        );

        List<String> argv = argvCaptor.getValue();
        // Python -m 模式：命令行含 -m + 模块路径 + 关键参数
        assertThat(argv).contains(
                "-m", "scripts.prompt_tuning.generate_candidate_prompts",
                "--audit_file", auditFile.toAbsolutePath().toString(),
                "--output_dir", outputDir.toAbsolutePath().toString(),
                "--overwrite"
        );
    }

    @Test
    void throwsWhenScriptTimesOut() throws Exception {
        when(processRunner.run(any(), any(), any(), any(), any())).thenReturn(
                ProcessExecutionResult.builder()
                        .command(List.of())
                        .exitCode(-1)
                        .stdout("")
                        .stderr("")
                        .elapsedSeconds(60L)
                        .timedOut(true)
                        .build()
        );

        assertThatThrownBy(() -> orchestrator.run(
                Path.of("/tmp/audit.json"), Path.of("/tmp/out"), null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.GATEWAY_TIMEOUT)
                .hasMessageContaining("超时");
    }

    @Test
    void throwsWhenScriptFails() throws Exception {
        when(processRunner.run(any(), any(), any(), any(), any())).thenReturn(
                ProcessExecutionResult.builder()
                        .command(List.of())
                        .exitCode(1)
                        .stdout("")
                        .stderr("Traceback (most recent call last):\n...\nRuntimeError: schema not found")
                        .elapsedSeconds(0L)
                        .timedOut(false)
                        .build()
        );

        assertThatThrownBy(() -> orchestrator.run(
                Path.of("/tmp/audit.json"), Path.of("/tmp/out"), null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ApiResultCode.CANDIDATE_GENERATION_FAILED.getCode());
    }
}
