package org.ysu.ckqaback.integration.process;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PythonCommandResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldTreatExamplePlaceholderAsMissingAndUseLocalCondaEnvPython() throws Exception {
        Path python = tempDir.resolve("miniconda3/envs/courseKg/bin/python");
        Files.createDirectories(python.getParent());
        Files.createFile(python);

        List<String> command = PythonCommandResolver.resolve(
                "/path/to/courseKg/bin/python",
                "courseKg",
                tempDir
        );

        assertThat(command).containsExactly(python.toString());
    }

    @Test
    void shouldKeepExplicitPythonPathWhenItIsNotAPlaceholder() {
        List<String> command = PythonCommandResolver.resolve(
                "/opt/conda/envs/courseKg/bin/python",
                "courseKg",
                tempDir
        );

        assertThat(command).containsExactly("/opt/conda/envs/courseKg/bin/python");
    }

    @Test
    void shouldFallbackToCondaRunWhenNoEnvPythonFileExists() {
        List<String> command = PythonCommandResolver.resolve("", "courseKg", tempDir);

        assertThat(command).containsExactly("conda", "run", "-n", "courseKg", "python");
    }
}
