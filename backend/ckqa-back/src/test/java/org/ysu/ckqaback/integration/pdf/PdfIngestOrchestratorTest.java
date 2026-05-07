package org.ysu.ckqaback.integration.pdf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.ysu.ckqaback.entity.CourseMaterials;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class PdfIngestOrchestratorTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldResolveExamplePythonPlaceholderBeforeRunningParseCommand() throws Exception {
        String originalHome = System.getProperty("user.home");
        Path python = tempDir.resolve("miniconda3/envs/courseKg/bin/python");
        Files.createDirectories(python.getParent());
        Files.createFile(python);

        try {
            System.setProperty("user.home", tempDir.toString());
            ProcessRunner processRunner = mock(ProcessRunner.class);
            CkqaIntegrationProperties properties = new CkqaIntegrationProperties();
            properties.getPdfIngest().setPython("/path/to/courseKg/bin/python");
            properties.getPdfIngest().setRoot(tempDir.resolve("pdf_ingest").toString());
            CourseMaterials material = new CourseMaterials();
            material.setId(8L);
            material.setCourseId("os");
            PdfIngestOrchestrator orchestrator = new PdfIngestOrchestrator(properties, processRunner);

            given(processRunner.run(any(), any(), any(), any(), any())).willReturn(success());

            orchestrator.parse(material);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<String>> commandCaptor = ArgumentCaptor.forClass(List.class);
            then(processRunner).should().run(
                    commandCaptor.capture(),
                    eq(tempDir.resolve("pdf_ingest")),
                    eq(Map.of()),
                    eq(Duration.ofSeconds(900)),
                    any(ProcessContext.class)
            );
            assertThat(commandCaptor.getValue()).containsExactly(
                    python.toString(),
                    "scripts/pdf_processor/mineru_parser.py",
                    "parse",
                    "os",
                    "--material-id",
                    "8",
                    "--allow-claimed-processing"
            );
        } finally {
            System.setProperty("user.home", originalHome);
        }
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
